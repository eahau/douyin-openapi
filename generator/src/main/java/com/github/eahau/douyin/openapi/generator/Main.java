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

import com.github.eahau.douyin.openapi.generator.api.DouYinOpenDocApi;
import com.github.eahau.douyin.openapi.generator.api.DouYinOpenDocApi.Children;
import com.github.eahau.douyin.openapi.generator.api.DouYinOpenDocApi.Data;
import com.github.eahau.douyin.openapi.generator.api.DouYinOpenDocApi.DocsResponse;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import feign.Feign;
import feign.Logger.Level;
import feign.gson.GsonDecoder;
import feign.slf4j.Slf4jLogger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class Main {

    static final DouYinOpenDocApi douYinOpenDocApi = Feign.builder()
            .logLevel(Level.BASIC)
            .logger(new Slf4jLogger(DouYinOpenDocApi.class))
            .decoder(new GsonDecoder())
            .target(DouYinOpenDocApi.class, Misc.DOC_BASE_URL);

    static final ExecutorService executorService = new ThreadPoolExecutor(
            30, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
            new CallerRunsPolicy()
    );

    @SneakyThrows
    public static void main(String[] args) {

        final Stopwatch stopwatch = Stopwatch.createStarted();

        final DocsResponse docsResponse = douYinOpenDocApi.allDocs();
        final List<Data> data = docsResponse.getData();

        for (final Data datum : data) {

            final AtomicReference<GeneratorContents> contents = new AtomicReference<>();

            final List<CompletableFuture<Void>> futures = Lists.newArrayList();

            datum.getChildren()
                    .stream()
                    .filter(it -> it.getTitle().contains("开发"))
                    .map(Children::getChildren)
                    .flatMap(Collection::stream)
                    .filter(it -> it.getTitle().contains("OpenAPI"))
                    .map(Children::getChildren)
                    .flatMap(Collection::stream)
                    .forEach(children -> {
                        if (contents.get() == null) {
                            contents.set(
                                    GeneratorContents.builder()
                                            .title(children.getTitle())
                                            .docPath(children.getPath())
                                            .build()
                            );
                        }

                        final List<Children> allChildren = children.flatChildren();
                        if (CollectionUtils.isEmpty(allChildren)) {
                            return;
                        }

                        final GeneratorContents generatorContents = contents.get();
                        generatorContents.addTag(children);

                        for (final Children child : allChildren) {

                            final String path = child.docPath();
                            final CompletableFuture<Void> future = CompletableFuture
                                    .supplyAsync(() -> douYinOpenDocApi.docs(path), executorService)
                                    .thenAccept(docResponse -> {
                                        docResponse.setPath(path);
                                        try {
                                            final GeneratorContent content = docResponse.toGeneratorContext();
                                            content.setComponents(generatorContents.getOpenAPI().getComponents());

                                            generatorContents.add(content);
                                        } catch (Exception e) {
                                            log.error("parse `{}` doc failed.", path, e);
                                        }
                                    });

                            futures.add(future);
                        }
                    });

            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}))
                        .thenAccept(ignored -> {
                            try {
                                final GeneratorContents generatorContents = contents.get();
                                generatorContents.generateOpenApi();
                            } catch (Throwable e) {
                                log.error("generateOpenApi failed.", e);
                            }
                        });
            }

        }

        while (!executorService.isTerminated()) {
            executorService.shutdown();
        }

        log.info("Done! Time elapsed {}", stopwatch.stop());
    }

}