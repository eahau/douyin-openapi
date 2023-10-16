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

import com.github.eahau.openapi.douyin.generator.DocField;
import com.github.eahau.openapi.douyin.generator.api.DouYinOpenDocApi.DocResponse;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.util.ast.ContentNode;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class MarkdownParser extends HtmlParser {

    public MarkdownParser(DocResponse response) {
        super(response);
    }

    private List<DocField> parseTable(TableBlock topTableBlock) {

        final int level = ((Heading) topTableBlock.getPrevious()).getLevel();

        final List<DocField> res = tableToDocFields(topTableBlock);

        final List<DocField> flatDocFields = Lists.newLinkedList(res);

        for (Node next = topTableBlock.getNext(); next != null; next = next.getNext()) {
            if (next instanceof Heading) {
                if (((Heading) next).getLevel() == level) {
                    break;
                }

                Node tableBlock = next.getNext();
                if (tableBlock instanceof TableBlock) {

                    final List<DocField> docFields = tableToDocFields(tableBlock);

                    flatDocFields.addAll(docFields);

                    final String headingString = next.getChars().toString();

                    flatDocFields.stream()
                            .filter(it -> headingString.contains(it.getName()))
                            .findFirst()
                            .ifPresent(docField -> docField.setChildren(docFields));
                }
            }
        }

        return res;
    }

    private List<DocField> parseTable(ContentNode topContentNode) {

        final int level = ((Heading) topContentNode.getPrevious()).getLevel();

        final List<DocField> res = tableToDocFields(topContentNode);

        final List<DocField> flatDocFields = Lists.newLinkedList(res);

        for (Node next = topContentNode.getNext(); next != null; next = next.getNext()) {
            if (next instanceof Heading) {
                if (((Heading) next).getLevel() == level) {
                    break;
                }

                Node contentNode = next.getNext();
                if (contentNode instanceof ContentNode) {

                    final List<DocField> docFields = tableToDocFields(contentNode);

                    flatDocFields.addAll(docFields);

                    final String headingString = next.getChars().toString();

                    flatDocFields.stream()
                            .filter(it -> headingString.contains(it.getName()))
                            .findFirst()
                            .ifPresent(docField -> docField.setChildren(docFields));
                }
            }
        }

        return res;
    }

    @Override
    protected void parseBaseInfo(final Node node) {
        final Node next = node.getNext();

        if (!(next instanceof TableBlock)) {
            super.parseBaseInfo(node);
            return;
        }

        final List<List<String>> table = tableToList(next)
                .stream()
                .map(Collection::stream)
                .map(it -> it.filter(StringUtils::isNotBlank).collect(Collectors.toList()))
                .collect(Collectors.toList());

        String httpUrl = null, httpMethod = null;

        rows:
        for (final List<String> rows : table) {
            if (ObjectUtils.allNotNull(httpUrl, httpMethod)) {
                break;
            }
            for (int i = 0; i < rows.size() - 1; i++) {
                final int nextIndex = i + 1;
                switch (rows.get(i)) {
                    case "接口URL":
                    case "HTTP URL": {
                        httpUrl = rows.get(nextIndex);
                        continue rows;
                    }
                    case "通信协议":
                    case "HTTP Method": {
                        httpMethod = rows.get(nextIndex);
                        continue rows;
                    }
                }
            }
        }

        final String[] httpUrlArray = httpUrl.split(".com");
        final String path = httpUrlArray.length == 2 ? httpUrlArray[1] : "{path_config}";

        builder.path(path).method(HttpMethod.valueOf(httpMethod.toUpperCase()));
    }

    @Override
    protected void parseHeader(final Node node) {
        final Node tableBlock = node.getNextAny(TableBlock.class);
        final List<DocField> docFields = tableToDocFields(tableBlock);
        builder.headFields(docFields);
    }

    @Override
    protected void parseQuery(Node node) {
        final Node tableBlock = node.getNextAny(TableBlock.class);
        final List<DocField> docFields = tableToDocFields(tableBlock);
        builder.queryFields(docFields);
    }

    @Override
    protected void parseBody(final Node node) {
        final TableBlock tableBlock = (TableBlock) node.getNextAny(TableBlock.class);
        final List<DocField> docFields = parseTable(tableBlock);
        builder.bodyFields(docFields);
    }

    @Override
    protected void parseResponse(final Node node) {
        final ContentNode contentNode = (ContentNode) node.getNextAny(ContentNode.class);
        final List<DocField> docFields = parseTable(contentNode);
        builder.respFields(docFields);
    }

    static List<List<String>> tableToList(Node node) {

        final Iterator<Node> childIterator = node.getChildIterator();
        final Node head = childIterator.next();
        // 忽略 head 和 body 之间的分隔符 “-----------”
        final Node headerSeparator = childIterator.next();

        return Stream.concat(Stream.of(head), Streams.stream(childIterator))
                .map(Node::getChildIterator)
                .flatMap(Streams::stream)
                .map(Node::getChildIterator)
                .map(it -> Streams.stream(it)
                        .filter(TableCell.class::isInstance)
                        .map(TableCell.class::cast)
                        .map(TableCell::getText)
                        .map(BasedSequence::toString)
                        .map(text -> text.replace("<br/>", "").replace("*", ""))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    static List<List<String>> contentToList(ContentNode node) {

        return node.getContentLines()
                .stream()
                // 是 "|" 分割的 table 且忽略全是 "-" 的分隔符
                .filter(it -> it.chars().anyMatch(c -> c == '|') && it.chars().noneMatch(c -> c == '-'))
                .map(String::valueOf)
                .map(it -> {
                    final String[] array = it.split("\\|");
                    return Arrays.stream(array)
                            .skip(1) // 由于是根据 "|" 分割成数组，忽略第一个和最后一个空字符串
                            .limit(array.length - 2)
                            .map(s -> s.replace("<br/>", "").replace("*", ""))
                            .map(String::trim)
                            .collect(Collectors.toList());
                })
                .collect(Collectors.toList());
    }

    static List<DocField> tableToDocFields(Node tableBlock) {

        if (tableBlock == null) {
            return Collections.emptyList();
        }

        final Iterator<List<String>> table = (tableBlock instanceof TableBlock ? tableToList(tableBlock) : contentToList((ContentNode) tableBlock)).iterator();

        final List<String> columns = table.next();

        final Map<Integer, DocField> lastParentColumn = Maps.newHashMap();

        return Streams.stream(table)
                .map(values -> {
                    final DocField docField = new DocField();

                    for (int i = 0; i < values.size(); i++) {
                        final String value = values.get(i);
                        if (StringUtils.isBlank(value)) {
                            continue;
                        }

                        String column = columns.get(i);
                        if (StringUtils.isBlank(column)) {
                            column = i == 1 ? columns.get(0) : Lists.reverse(columns.subList(0, i))
                                    .stream()
                                    .filter(StringUtils::isNotBlank)
                                    .findFirst()
                                    .orElse("");
                        }
                        final BiConsumer<DocField, String> setter = DocField.getByColumnName(column);
                        if (setter != null) {
                            setter.accept(docField, value);
                        }
                    }

                    final String name = docField.getName();
                    final int nameIndex;
                    if (name.contains(".")) {
                        final int dotCount = StringUtils.countMatches(name, '.');
                        nameIndex = dotCount;
                        docField.setName(name.substring(dotCount));
                    } else {
                        nameIndex = values.indexOf(name);
                    }

                    final DocField parent = lastParentColumn.get(nameIndex - 1);
                    if (parent != null) {

                        docField.setParent(parent);
                        parent.getChildren().add(docField);
                    }

                    if (docField.isObjectType() || docField.isArrayObject()) {
                        lastParentColumn.put(nameIndex, docField);
                    }

                    return docField;
                })
                .collect(Collectors.toList());
    }

}
