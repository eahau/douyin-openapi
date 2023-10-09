/*
 * Copyright 2023 eahau@foxmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.eahau.openapi.douyin.generator;

import com.github.eahau.openapi.douyin.generator.api.DouYinOpenDocApi.Children;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Getter
@Builder
public class GeneratorContents extends LinkedList<GeneratorContent> {

    private final String title;

    private final String docPath;

    final OpenAPI openAPI = new OpenAPI().components(new Components().schemas(Maps.newLinkedHashMap()));

    private final List<Tag> tags = Lists.newLinkedList();

    public void addTag(Children children) {
        final String[] pathArray = children.getPath().split("/");

        final String title = children.getTitle();

        final Tag tag = new Tag()
                .name(pathArray[pathArray.length - 1])
                .description(title)
                .externalDocs(new ExternalDocumentation().url(children.docUrl()).description(title));

        tags.add(tag);
    }

    @SneakyThrows
    public void generateOpenApi() {

        final OpenAPI openAPI = this.openAPI;

        stream()
                .filter(GeneratorContent::accept)
                // 根据 path 排序，避免每次构建后 openapi.json 因为顺序改变而产生 change
                .sorted(Comparator.comparing(GeneratorContent::getPath))
                .peek(it -> tags.stream()
                        .filter(tag -> it.getDocPath().contains(tag.getName()))
                        .findFirst()
                        .map(Tag::getName)
                        .ifPresent(it::setTag))
                .forEach(it -> {
                    final PathItem pathItem = it.toPathItem();
                    if (pathItem != null) {
                        openAPI.path(it.getPath(), pathItem);

                        final Components components = it.getComponents();
                        if (components.getSchemas() != null) {
                            components.getSchemas().forEach(openAPI.getComponents()::addSchemas);
                        }
                    }
                });

        final String docPath = getDocPath();

        openAPI.info(
                new Info()
                        .title(getTitle())
                        .version("0.0.1")
        )
                .externalDocs(
                        new ExternalDocumentation()
                                .description(getTitle())
                                .url(Misc.DOC_BASE_URL + Misc.DOC_URI + docPath)
                )
                .tags(getTags())
                .addServersItem(new Server().url(Misc.API_BASE_URL));

        final String domain = Arrays.stream(docPath.split("/"))
                .filter(StringUtils::isNotBlank)
                .filter(it -> !it.equals(Misc.DOC_LANGUAGE))
                .findFirst()
                .get();

        mergeOpenApi(domain, openAPI);

        final String filename = "openapi.json";

        final String openApiContent = Json.pretty(openAPI);

        FileUtils.write(newFile(domain, filename), openApiContent, StandardCharsets.UTF_8);

    }

    File newFile(String domain, String filename) {

        final String fullFilename = String.join(
                "/",
                System.getProperty("user.dir"),
                domain,
                "/src/main/resources",
                filename
        );

        return new File(fullFilename);
    }

    /**
     * 合并自动生成的和人工维护的 openapi.json, 以人工维护的为准.
     */
    @SneakyThrows
    void mergeOpenApi(String domain, OpenAPI openAPI) {

        final String filename = "openapi-manual.json";

        final String openapiManualJson = FileUtils.readFileToString(newFile(domain, filename), StandardCharsets.UTF_8);

        final OpenAPI openApiManual = Json.mapper().readValue(openapiManualJson, OpenAPI.class);

        // merge path
        Optional.ofNullable(openApiManual)
                .map(OpenAPI::getPaths)
                .ifPresent(it -> it.forEach(openAPI.getPaths()::addPathItem));

        // merge schema
        Optional.ofNullable(openApiManual)
                .map(OpenAPI::getComponents)
                .map(Components::getSchemas)
                .ifPresent(it -> it.forEach(openAPI.getComponents()::addSchemas));

    }

}
