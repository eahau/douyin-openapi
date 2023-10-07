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

import com.github.eahau.douyin.openapi.generator.Misc;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface DouYinOpenApiListApi {

    @Getter
    class ApiListResponse {

        private String style;
        private long _sort;
        private boolean deleted;
        private String path;
        private int status;
        private String app_id;
        private boolean is_dir;
        private Date updated_at;
        private Date created_at;
        private Date saved_time;
        private List<String> tags;
        private boolean open_market;
        private int usage;
        private String publisher;
        private PublishUser publish_user;
        private int type;
        private String script;
        private Content content;
        private String title;
        private String version_id;
        private Date publish_at;
        private int visible;
        private String scope;
        private int approval_status;
        private String lang;
        private String business_id;
        private String creator;
        private String keywords;
        private String zh_doc_id;
        private String _id;
        private boolean is_modified;
        private int view_count;
        private int __v;

        public Map<String, OpsList> getOps() {
            ContentChildren ch = getContent().getChildren().get(0);
            while (ch.getProps() == null || ch.getProps().value == null) {
                ch = ch.getChildren().get(0);
            }

            return ch.getProps().value.t;
        }

        public static ApiListResponse fromJson(String json) {
            return Misc.GSON.fromJson(json, ApiListResponse.class);
        }

    }

    @Getter
    class PublishUser {

        private long id;
        private String name;
        private String email;
        private String avatar_url;
    }

    @Getter
    class Ops {

        static final JsonObject empty = new JsonObject();

        private JsonElement insert = JsonNull.INSTANCE;
        private JsonObject attributes = empty;

        public String getId() {
            final JsonElement insert = this.insert;
            if (insert.isJsonPrimitive()) {
                final String id = insert.getAsString();
                if (StringUtils.isNotBlank(id)) {
                    return id;
                }
            } else {
                final JsonElement idElement = insert.getAsJsonObject().get("id");

                if (idElement != null) {
                    final String id = idElement.getAsString();
                    if (StringUtils.isNotBlank(id)) {
                        return id;
                    }
                }
            }

            return attributes.get("zoneId").getAsString();
        }

        public boolean isBold() {
            final JsonObject attributes = this.attributes;
            return attributes.has("bold") && attributes.get("bold").getAsBoolean();
        }

        public boolean isHeading() {
            final JsonObject attributes = this.attributes;
            return attributes.has("heading");
        }

        public boolean isList() {
            final JsonObject attributes = this.attributes;
            return attributes.has("list");
        }

        public boolean isTable() {
            final JsonObject attributes = this.attributes;
            return attributes.has("aceTable");
        }

        public String getDataId() {
            final JsonObject attributes = this.attributes;
            return !isTable() ? "" : attributes.get("aceTable").getAsString().split(" ")[0];
        }

        public String getColumnWidthId() {
            final JsonObject attributes = this.attributes;
            return !isTable() ? "" : attributes.get("aceTable").getAsString().split(" ")[1];
        }

    }

    @Getter
    class Content {

        private List<ContentChildren> children;
    }

    @Getter
    class ContentChildren {

        private List<ContentChildren> children;
        private String componentId;
        private String type;
        private String id;
        private String componentType;
        private Config config;
        private Props props;
    }

    @Getter
    class Config {

        private int w;
        private String i;
        private boolean isResizable;
        private int h;
        private int x;
        private int y;
        private boolean moved;
        private boolean isDraggable;
    }

    @Getter
    class Props {
        private PropsValue value;
    }

    @Getter
    class PropsValue {
        private LinkedHashMap<String, OpsList> t;
    }

    @Getter
    class OpsList {
        private List<Ops> ops;

        private String zoneId;

        public String getColumnId() {
            final String zoneId = this.zoneId;
            final int index = zoneId.lastIndexOf('x');
            if (index == -1) {
                return zoneId;
            }

            return zoneId.substring(index + 1);
        }

        private String zoneType;
    }

}
