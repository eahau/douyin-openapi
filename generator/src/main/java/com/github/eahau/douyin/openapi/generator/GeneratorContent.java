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
package com.github.eahau.douyin.openapi.generator;

import com.google.common.base.CaseFormat;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Getter
@Builder
public class GeneratorContent {

    private final String title;

    private final String desc;

    private final HttpMethod method;

    @Singular("addServer")
    private final List<Server> serverList;

    private final String path;

    String schemaPrefix;

    String getSchemaPrefix() {
        if (schemaPrefix == null) {
            final String[] pathArray = getPath().split("/");
            this.schemaPrefix = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, pathArray[pathArray.length - 1]);
        }
        return schemaPrefix;
    }

    private final String docPath;

    @Setter
    private String tag;

    private final Map<String, String> params;

    private final String requestJson;

    private final String responseJson;

    private final String errorResponseJson;

    @Default
    private final List<DocField> headFields = Collections.emptyList();

    @Default
    private final List<DocField> queryFields = Collections.emptyList();

    @Default
    private final List<DocField> bodyFields = Collections.emptyList();

    @Default
    private final List<DocField> respFields = Collections.emptyList();

    private final boolean respFieldNeedRebuild;

    @Default
    @Setter
    private Components components = new Components().schemas(Maps.newLinkedHashMap());

    RequestBody getRequestBody() {
        final List<DocField> bodyFields = getBodyFields();

        final String name;
        if (bodyFields.stream().anyMatch(DocField::isBinaryType)) {
            name = "multipart/form-data";
        } else {
            name = "application/json";
        }

        return new RequestBody()
                .required(!bodyFields.isEmpty())
                .description(getDesc())
                .content(
                        new Content()
                                .addMediaType(
                                        name,
                                        new MediaType()
                                                .schema(buildReqBodySchema())
                                )
                );
    }

    Schema<Object> buildReqBodySchema() {
        final Schema<Object> rootSchema = new ObjectSchema();

        getBodyFields()
                .stream()
                .filter(DocField::isRootObject)
                .forEach(it -> rootSchema.addProperty(it.getName(), it.toSchema()));

        return rootSchema;
    }

    Schema<Object> buildRespSchema() {

        final List<DocField> respFields = getRespFields();

        if (isRespFieldNeedRebuild()) {

            final Function<String, Object> pathReader = new Function<String, Object>() {

                final Supplier<DocumentContext> documentContext = Suppliers.memoize(() -> {
                    try {
                        return JsonPath.parse(getResponseJson());
                    } catch (RuntimeException ignored) {
                        return JsonPath
                                .using(Configuration.defaultConfiguration().jsonProvider(new JsonSmartJsonProvider()))
                                .parse(getResponseJson());
                    }
                });

                @Override
                public Object apply(final String path) {
                    final Object value = documentContext.get().read(path);
                    if (value instanceof List) {
                        if (((List<?>) value).isEmpty()) {
                            return null;
                        }
                    }

                    return value;
                }
            };

            final boolean hasOtherStructField = respFields.stream()
                    .filter(it -> !StringUtils.equalsAny(it.getName(), "extra", "data"))
                    .anyMatch(DocField::isObjectType);

            final Iterator<DocField> iterator = respFields.iterator();
            DocField lastStructDocField = null;

            while (iterator.hasNext()) {
                final DocField docField = iterator.next();
                final String name = docField.getName();
                if (docField.isArrayOrObject()) {
                    Object value;
                    if (!hasOtherStructField || (value = pathReader.apply(name)) != null) {
                        lastStructDocField = docField;
                    } else {

                        lastStructDocField.getChildren().add(docField);
                        docField.setParent(lastStructDocField);

                        String fullName = docField.getFullName();
                        value = pathReader.apply(fullName);
                        if (value != null) {
                            lastStructDocField = docField.isArrayOrObject() ? docField : docField.getParent();
                        }
                    }
                } else {
                    if (hasOtherStructField && docField.getParent() == null) {
                        docField.setParent(lastStructDocField);
                        String fullName = docField.getFullName();
                        Object value = pathReader.apply(fullName);

                        // 文档 有的缺字段，和返回的 json 不一致
                        if (value == null) {
                            value = pathReader.apply(String.join(".", lastStructDocField.getParentName(), name));
                            if (value != null) {
                                lastStructDocField = lastStructDocField.getParent();
                            }
                            docField.setParent(lastStructDocField);
                        }
                    }

                    if (lastStructDocField != null) {
                        lastStructDocField.getChildren().add(docField);
                    }

                }
            }
        }

        final Schema<Object> responseSchema = new ObjectSchema().name(getSchemaPrefix() + "Response");

        getComponents().addSchemas(responseSchema.getName(), responseSchema);

        final Schema<Object> rootSchema = new ObjectSchema().$ref(responseSchema.getName());

        respFields.forEach(docField -> {
            final Schema<?> schema = docField.toSchema();
            if (MapUtils.isNotEmpty(schema.getProperties()) || schema.getItems() != null) {
                addSchema(responseSchema, schema);
            } else {
                responseSchema.addProperty(docField.getName(), schema);
            }
        });

        return rootSchema;
    }

    void addSchema(Schema<?> parentSchema, Schema<?> schema) {
        final Schema<?> items = schema.getItems();

        final Map<String, Schema> properties = (items != null ? items : schema).getProperties();

        if (MapUtils.isNotEmpty(properties)) {
            final String name = schema.getName();

            final Components components = getComponents();

            final Schema existSchema = components.getSchemas().get(name);
            if (schema.equals(existSchema)) {
                parentSchema.addProperty(name, new Schema().$ref(existSchema.get$ref()));
                return;
            }

            final String schemaFullName = getSchemaPrefix() + (CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name));

            if (items != null) {
                schema.setItems(null);
            }
            components.addSchemas(schemaFullName, schema);

            parentSchema.addProperty(name, new Schema().$ref(schemaFullName));

            for (final Schema value : properties.values()) {
                addSchema(schema, value);
            }
        }

    }

    ApiResponses getApiResponses() {

        final Schema<Object> schema = buildRespSchema();

        return new ApiResponses()
                ._default(
                        new ApiResponse()
                                .description(getTitle())
                                .content(
                                        new Content()
                                                .addMediaType("application/json",
                                                        new MediaType()
                                                                .schema(schema)
                                                                .addExamples("succeed", new Example().value(getResponseJson()))
                                                                .addExamples("failed", new Example().value(getErrorResponseJson()))
                                                )
                                )
                );
    }

    @SneakyThrows
    Operation getOperation() {

        final Operation operation = new Operation()
                .addTagsItem(getTag())
                .description(getDesc())
                .addServersItem(new Server().url("https://open.douyin.com/"));

        final HttpMethod httpMethod = getMethod();
        if (httpMethod == HttpMethod.GET) {
            getQueryFields()
                    .stream()
                    .map(it -> it.toParameter(QueryParameter::new))
                    .forEach(operation::addParametersItem);
        } else {
            operation.requestBody(getRequestBody());
        }

        operation.responses(getApiResponses());

        return operation;
    }

    ExternalDocumentation toExternalDocumentation() {
        return new ExternalDocumentation()
                .description(getTitle())
                .url(Misc.DOC_BASE_URL + getDocPath());
    }

    public PathItem toPathItem() {

        if (getMethod() == null) {
            return null;
        }

        final String path = getPath();
        if (path == null) {
            log.warn("docPath {}, path is null, ignored.", Misc.DOC_BASE_URL + getDocPath());
            return null;
        }

        final PathItem pathItem = new PathItem();
        try {
            pathItem.operation(getMethod(), getOperation());
        } catch (Exception e) {
            log.error("docPath {} , build Operation failed.", Misc.DOC_BASE_URL + getDocPath(), e);
            throw e;
        }

        pathItem.servers(getServerList());

        return pathItem;
    }

}
