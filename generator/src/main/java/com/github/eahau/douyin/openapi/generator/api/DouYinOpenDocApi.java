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
package com.github.eahau.douyin.openapi.generator.api;

import com.github.eahau.douyin.openapi.generator.GeneratorContent;
import com.github.eahau.douyin.openapi.generator.Misc;
import com.github.eahau.douyin.openapi.generator.api.DouYinOpenApiListApi.ApiListResponse;
import com.github.eahau.douyin.openapi.generator.parser.HtmlParser;
import com.github.eahau.douyin.openapi.generator.parser.JsonDocParser;
import com.google.common.collect.Lists;
import feign.Param;
import feign.RequestLine;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

public interface DouYinOpenDocApi {

    @RequestLine("GET {path}?__loader=" + Misc.DOC_LANGUAGE + "/$")
    DocResponse docs(@Param("path") String path);

    @Getter
    @ToString
    @Setter
    class DocResponse {

        /**
         * content 类型，据观察 2=html.
         */
        private int type;

        private String title;

        private String keywords;

        private String description;

        private String content;

        private boolean isShowUpdateTime;

        private String updateTime;

        private String arcositeId;

        public String path;

        public boolean isJson() {
            return type == 1;
        }

        public boolean isMarkdownHeadHtmlBody() {
            return type == 2;
        }

        public GeneratorContent toGeneratorContext() {
            if (isJson()) {
                final ApiListResponse apiListResponse = ApiListResponse.fromJson(getContent());

                final String markdown = new JsonDocParser(apiListResponse.getOps()).toMarkdown();
                setContent(markdown);

//                return new MarkdownParser(this).parse();
            }

            // isMarkdownHeadHtmlBody
            return new HtmlParser(this).parse();
        }

    }

    @RequestLine("GET /docs_v2/directory")
    DocsResponse allDocs();

    @Getter
    class DocsResponse {
        private int error;
        private List<Data> data;
    }

    @Getter
    class Data {

        private int PositionOrder;
        private String Title;
        private int Type;
        private String NodeId;
        private int Online;
        private String Path;
        private List<Children> Children;
        private String ParentNodeId;

    }

    @Getter
    class Children {

        private String Brief;
        private String Keywords;
        private int EditorType;
        private int isShowUpdateTime;
        private int PositionOrder;
        private String Title;
        private int Type;
        private String NodeId;
        private int Online;
        private String Path;
        private List<Children> Children;
        private String ParentNodeId;

        public String docPath() {
            return Misc.DOC_URI + getPath();
        }

        public String docUrl() {
            return Misc.DOC_BASE_URL + docPath();
        }

        public List<Children> flatChildren() {
            final List<Children> children = Children;

            if (CollectionUtils.isEmpty(children)) {
                return Collections.emptyList();
            }

            final List<Children> list = Lists.newLinkedList();

            for (final DouYinOpenDocApi.Children child : children) {
                if (CollectionUtils.isEmpty(child.getChildren())) {
                    list.add(child);
                } else {
                    list.addAll(child.flatChildren());
                }
            }

            return list;
        }
    }

}
