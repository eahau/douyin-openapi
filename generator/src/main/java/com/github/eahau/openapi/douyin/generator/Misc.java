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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public interface Misc {

    String DOC_BASE_URL = "https://developer.open-douyin.com";

    String DOC_URI = "/docs/resource";

    String DOC_LANGUAGE = "zh-CN";

    String DEFAULT_VALUE_KEY = "固定值";

    Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

}
