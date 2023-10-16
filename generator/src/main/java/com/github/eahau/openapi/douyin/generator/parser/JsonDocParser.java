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
package com.github.eahau.openapi.douyin.generator.parser;

import com.github.eahau.openapi.douyin.generator.api.DouYinOpenApiListApi.Ops;
import com.github.eahau.openapi.douyin.generator.api.DouYinOpenApiListApi.OpsList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import j2html.TagCreator;
import j2html.tags.DomContent;
import j2html.tags.specialized.TableTag;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.map.DefaultedMap;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
public class JsonDocParser {

    private final Map<String, OpsList> opsListMap;

    public String toMarkdown() {
        final String rootId = "0";
        return getOpsList(rootId)
                .getOps()
                .stream()
                .map(this::toMarkdown)
                .collect(Collectors.joining());
    }

    private String toMarkdown(Ops ops) {
        final JsonElement insert = ops.getInsert();
        if (insert.isJsonPrimitive()) {
            final JsonObject attributes = ops.getAttributes();

            final String value = insert.getAsString();

            if (attributes.isEmpty()) {
                return value;
            }

//            if (ops.isBold()) {
//                return new BoldText(value).toString();
//            }

            if (ops.isHeading()) {
                final String heading = attributes.get("heading").getAsString();
                final int level = heading.charAt(heading.length() - 1) - '0';
                return StringUtils.repeat('#', level) + " ";
            }

            if (ops.isList()) {
                final String bullet = attributes.get("list").getAsString();
                final int level = bullet.charAt(bullet.length() - 1) - '0';
                return StringUtils.repeat(" ", (level - 1) * 2) + value + " ";
            }

            if (attributes.has("type")) {
                if ("codeblock".equals(attributes.get("type").getAsString())) {
                    final String language = attributes.get("language").getAsString();
                    return "```" + language + System.lineSeparator()
                            + getSourceFromOps(ops, (o1, o2) -> 0) + System.lineSeparator()
                            + "```";
                }
            }

            if (ops.isTable()) {
                return buildTable(ops);
            }

            if (value.trim().equals("*")) {
                return "";
            }

            return value;
        }

        return String.join("", getSourceFromOps(ops, (o1, o2) -> 0));
    }

    private String buildTable(Ops ops) {
        final List<Ops> columnWidthList = getOpsList(ops.getColumnWidthId()).getOps();

        final Map<String, Integer> columnIndexMap = DefaultedMap.defaultedMap(columnWidthList
                .stream()
                .collect(Collectors.toMap(
                        Ops::getId,
                        columnWidthList::indexOf
                )), 0);

        final List<Ops> dataOpsList = getOpsList(ops.getDataId()).getOps();

        final Comparator<OpsList> comparator = Comparator.comparing(((Function<OpsList, String>) OpsList::getColumnId).andThen(columnIndexMap::get), Integer::compare);

        final TableTag table = TagCreator.table();

        final List<List<String>> tableList = dataOpsList.stream()
                .map(it -> getSourceFromOps(it, comparator))
                .map(Collection::stream)
                .map(it -> it
                        .map(s -> s.replace("\n", ""))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        for (int i = 0; i < tableList.size(); i++) {
            final Function<String, DomContent> mapper = i == 0 ? TagCreator::th : TagCreator::td;
            table.with(
                    TagCreator.tr(
                            tableList.get(i)
                                    .stream()
                                    .map(mapper)
                                    .toArray(DomContent[]::new)
                    )
            );
        }

        return table.render() + "\n";
    }

    private OpsList getOpsList(String id) {
        return opsListMap.get(id);
    }

    private List<String> getSourceFromOps(Ops ops, Comparator<OpsList> comparator) {
        final String id = ops.getId();
        final OpsList opsList = getOpsList(id);

        final List<List<Ops>> opsLists;
        if (opsList != null) {
            opsLists = Collections.singletonList(opsList.getOps());
        } else {
            opsLists = opsListMap
                    .values()
                    .stream()
                    .filter(it -> it.getZoneId().contains(id))
                    .sorted(comparator)
                    .map(OpsList::getOps)
                    .collect(Collectors.toList());
        }

        return opsLists.stream()
                .map(it -> it.stream().map(this::toMarkdown).collect(Collectors.joining()))
                .collect(Collectors.toList());
    }

}
