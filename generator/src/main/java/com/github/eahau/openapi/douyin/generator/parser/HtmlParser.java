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
import com.github.eahau.openapi.douyin.generator.GeneratorContent;
import com.github.eahau.openapi.douyin.generator.GeneratorContent.GeneratorContentBuilder;
import com.github.eahau.openapi.douyin.generator.Misc;
import com.github.eahau.openapi.douyin.generator.api.DouYinOpenDocApi.DocResponse;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.Streams;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Configuration.Defaults;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.ListBlock;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.servers.Server;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class HtmlParser {

    static final Parser PARSER = Parser.builder()
            .extensions(Collections.singletonList(TablesExtension.create()))
            .build();

    static {
        Configuration.setDefaults(
                new Defaults() {

                    final JsonProvider jsonProvider = new GsonJsonProvider(Misc.GSON);

                    final MappingProvider mappingProvider = new GsonMappingProvider(Misc.GSON);

                    @Override
                    public JsonProvider jsonProvider() {
                        return jsonProvider;
                    }

                    @Override
                    public Set<Option> options() {
                        return EnumSet.of(Option.SUPPRESS_EXCEPTIONS);
                    }

                    @Override
                    public MappingProvider mappingProvider() {
                        return mappingProvider;
                    }
                }
        );
    }

    final DocResponse response;

    final com.vladsch.flexmark.util.ast.Document markdown;

    final GeneratorContentBuilder builder = GeneratorContent.builder();

    final List<Consumer<Heading>> consumers;

    /**
     * 只处理匹配的 heading 的子节点.
     */
    private void visit(Heading heading, Consumer<Node> consumer) {
        for (Node next = heading.getNext(); next != null; next = next.getNext()) {
            if (next instanceof Heading && ((Heading) next).getLevel() == heading.getLevel()) {
                break;
            }

            consumer.accept(next);
        }
    }

    public HtmlParser(final DocResponse response) {
        this.response = response;
        this.builder.title(response.getTitle()).docPath(response.getPath());
        this.markdown = PARSER.parse(response.getContent());

        this.consumers = Arrays.asList(
                it -> {
                    if (it.getLevel() == 2 && StringUtils.equals(getHeadingText(it), "接口说明")) {

                        final StringBuilder desc = new StringBuilder();
                        visit(it, node -> desc.append(node.getChars()).append("\n"));
                        builder.desc(desc.toString());
                    }
                },
                it -> {
                    if (StringUtils.equalsAny(getHeadingText(it), "请求地址", "基本信息")) {
                        parseBaseInfo(it);
                    }
                },
                it -> {
                    if (it.getLevel() == 2 && StringUtils.equals(getHeadingText(it), "请求头")) {
                        parseHeader(it);
                    }
                },
                it -> {
                    if (it.getLevel() == 2 && StringUtils.equals(getHeadingText(it), "请求参数")) {
                        final Node next = it.getNext();
                        if (next instanceof Heading && StringUtils.equalsAny(((Heading) next).getText(), "Query", "URL 请求")) {
                            parseQuery(it);
                        } else {
                            parseBody(it);
                        }
                    }
                },
                it -> {
                    if (it.getLevel() == 2 && StringUtils.contains(getHeadingText(it), "响应参数")) {
                        parseResponse(it);
                    }
                },
                it -> {
                    if (it.getLevel() != 3) {
                        return;
                    }

                    if (StringUtils.containsAny(getHeadingText(it), "响应样例", "正常")) {
                        parseResponseExample(it);
                    }
                    if (StringUtils.containsAny(getHeadingText(it), "异常", "失败", "错误")) {
                        parseErrorResponseExample(it);
                    }
                }
        );
    }

    static Map<String, String> tableToMap(Document document) {
        final Elements tr = document.select("tr");
        final Elements elements = tr.isEmpty() ? document.select("td") : tr;

        final Map<String, String> map = Maps.newHashMap();
        for (final Element e : elements) {
            final Elements td = e.select("td");
            final List<String> textList = (td.isEmpty() ? e.select("th") : td).eachText();
            if (textList.size() == 2) {
                map.put(textList.get(0), textList.get(1));
            }
        }

        return map;
    }

    static final Pattern pattern = Pattern.compile("((https?|http):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)", Pattern.CASE_INSENSITIVE);

    /**
     * copy from https://stackoverflow.com/a/28269120.
     */
    @SneakyThrows
    static List<URL> extractUrls(String text) {
        List<URL> containedUrls = new ArrayList<>();

        Matcher urlMatcher = pattern.matcher(text);

        while (urlMatcher.find()) {
            final URL url = new URL(text.substring(urlMatcher.start(0), urlMatcher.end(0)));
            containedUrls.add(url);
        }

        return containedUrls;
    }

    protected void parseBaseInfo(Node node) {
        if (node.getNext() instanceof HtmlBlock) {
            Document document = Jsoup.parse(node.getNext().getChars().toString());

            final Map<String, String> baseInfoMap = tableToMap(document);

            Optional.ofNullable(baseInfoMap.get("HTTP URL"))
                    .ifPresent(httpUrl -> {
                        final List<URL> urls = extractUrls(httpUrl);
                        if (urls.isEmpty()) {

                            if (httpUrl.contains("/") && httpUrl.chars().allMatch(c -> CharUtils.isAscii((char) c))) {
                                builder.path(httpUrl);
                            } else {

                                final String[] pathArray = response.getPath().split("/");

                                final int length = pathArray.length;

                                final String path = "/" + String.join("/", ArrayUtils.subarray(pathArray, length - 2, length));

                                builder.path(path);
                            }
                        } else {
                            final StringTokenizer tokenizer = httpUrl.contains("：") ? new StringTokenizer(httpUrl, "：") : null;
                            for (final URL url : urls) {

                                String desc = (tokenizer != null && tokenizer.hasMoreTokens()) ? tokenizer.nextToken() : null;

                                if (desc != null) {
                                    desc = desc.chars()
                                            .filter(c -> !CharUtils.isAscii((char) c))
                                            .mapToObj(c -> String.valueOf((char) c))
                                            .collect(Collectors.joining());
                                }

                                builder.path(url.getPath())
                                        .addServer(
                                                new Server()
                                                        .url(url.getProtocol() + ":" + url.getHost())
                                                        .description(desc)
                                        );
                            }
                        }
                    });

            Optional.ofNullable(baseInfoMap.get("HTTP Method"))
                    .map(String::toUpperCase)
                    .map(HttpMethod::valueOf)
                    .ifPresent(builder::method);
        } else {

            visit((Heading) node, next -> {
                if (next instanceof Block) {
                    final String text = next.getChildChars().toString().trim();
                    final StringTokenizer stringTokenizer = new StringTokenizer(text, "[]");

                    while (stringTokenizer.hasMoreTokens()) {
                        final String token = stringTokenizer.nextToken();

                        final Optional<HttpMethod> httpMethodOptional = Arrays.stream(HttpMethod.values())
                                .filter(it -> token.startsWith(it.name()))
                                .findFirst();

                        if (httpMethodOptional.isPresent()) {
                            builder.method(httpMethodOptional.get());
                            final String pathOrUrl = token.split(" ")[1];
                            final String path;
                            final List<URL> urls = extractUrls(token);
                            if (urls.isEmpty()) {
                                path = pathOrUrl;
                            } else {
                                final URL url = urls.get(0);
                                path = url.getPath();
                                builder.addServer(
                                        new Server()
                                                .url(url.getProtocol() + ":" + url.getHost())
                                                .description(
                                                        Optional.ofNullable(next.getPrevious())
                                                                .map(Node::getChildChars)
                                                                .map(String::valueOf)
                                                                .orElse(null)
                                                )
                                );
                            }

                            builder.path(path);
                            break;
                        }
                    }
                }
            });
        }

    }

    protected void parseHeader(Node node) {
        Node next = node.getNext();
        if (next instanceof HtmlBlock) {
            Document document = Jsoup.parse(next.getChars().toString());
            builder.headFields(parseHead(document));
        } else if (next instanceof ListBlock) {
            final List<String> textList = Streams.stream(next.getChildIterator())
                    .map(Node::getChildChars)
                    .map(BasedSequence::trim)
                    .map(BasedSequence::toString)
                    .collect(Collectors.toList());

            builder.headFields(parseHead(textList));
        }

        if (StringUtils.contains(next.getChars(), "通用参数-平台请求开发者公共参数")) {
            return;
        }

        if (next.getNext() instanceof Heading) {
            if (((Heading) next.getNext()).getText().isEmpty()) {
                parseBaseInfo(next.getNext());
            }
        }
    }

    protected void parseQuery(Node node) {
        Stream.of(HtmlBlock.class, Paragraph.class)
                .map(node::getNextAny)
                .filter(Objects::nonNull)
                .findFirst()
                .map(Node::getChars)
                .map(String::valueOf)
                .map(Jsoup::parse)
                .map(this::parseTableOrData)
                .ifPresent(builder::queryFields);
    }

    protected void parseBody(Node node) {
        Optional.ofNullable(node.getNextAny(HtmlBlock.class))
                .filter(HtmlBlock.class::isInstance)
                .map(HtmlBlock.class::cast)
                .map(this::parseTable)
                .ifPresent(builder::bodyFields);
    }

    private String getHeadingText(Node node) {
        if (node == null) {
            return null;
        }

        return Streams.stream(node.getChildIterator())
                .filter(it -> it instanceof Text)
                .findFirst()
                .map(Node::getChars)
                .map(BasedSequence::toString)
                .orElse(null);
    }

    /**
     * 获取当前节点带有 字段（英文）定义的 heading/paragraph.
     */
    private String getPreHeadingText(Node node, Predicate<String> existObjectClassName) {
        if (node == null) {
            return null;
        }

        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Text) {
                break;
            }
            child = child.getFirstChild();
        }

        return Optional.ofNullable(child)
                .map(Node::getChars)
                .map(it -> it.split(" "))
                .flatMap(array -> Arrays.stream(array)
                        .map(it -> it.chars()
                                .filter(c -> ((char) c) == '_' || CharUtils.isAsciiAlpha((char) c))
                                .mapToObj(c -> (char) c)
                                .map(String::valueOf)
                                .collect(Collectors.joining()))
                        .findFirst()
                        .map(String::valueOf))
                .filter(StringUtils::isNotBlank)
                .filter(existObjectClassName)
                .orElseGet(() -> getPreHeadingText(node.getPrevious(), existObjectClassName));
    }

    private List<DocField> parseTable(HtmlBlock topHtmlBlock) {

        final int level = ((Heading) topHtmlBlock.getPreviousAny(Heading.class)).getLevel();

        Document document = Jsoup.parse(topHtmlBlock.getChars().toString());
        final List<DocField> res = tableToDocFields(document);

        final Map<Object, DocField> objectTypeDocFieldMap = new LinkedHashMap<Object, DocField>() {

            String keyOf(Object key) {
                if (key instanceof CharSequence) {
                    return key.toString();
                }

                final DocField docField = (DocField) key;
                return String.join(":", docField.getName(), docField.getType());
            }

            @Override
            public boolean containsKey(final Object key) {
                return get(key) != null;
            }

            @Override
            public DocField get(final Object key) {
                return super.get(keyOf(key));
            }

            @Override
            public DocField put(final Object key, final DocField value) {
                return super.put(keyOf(key), value);
            }
        };


        Stream.concat(res.stream(), res.stream()
                .map(DocField::flatChildren)
                .flatMap(Collection::stream))
                .filter(DocField::isArrayOrObject)
                .forEach(it -> objectTypeDocFieldMap.put(it, it));

        Node next = topHtmlBlock.getNextAny(Heading.class);
        while (next != null) {

            if ((next instanceof Heading && (((Heading) next).getLevel() <= level))) {
                break;
            }

            Node htmlBlock = next.getNext();
            for (; htmlBlock != null; next = htmlBlock = htmlBlock.getNext()) {

                if (htmlBlock instanceof Heading) {
                    if (((Heading) htmlBlock).getLevel() <= level) {
                        break;
                    }
                    continue;
                }

                document = Jsoup.parse(htmlBlock.getChars().toString());
                final List<DocField> docFields = tableToDocFields(document);

                if (docFields.isEmpty()) {
                    continue;
                }

                docFields.stream()
                        .filter(DocField::isArrayOrObject)
                        .filter(it -> !objectTypeDocFieldMap.containsKey(it))
                        .forEach(it -> objectTypeDocFieldMap.put(it, it));

                final String clzName = getPreHeadingText(
                        next,
                        testClzName -> objectTypeDocFieldMap.values()
                                .stream()
                                .anyMatch(it -> {
                                    // 下文标题==上文提到的字段名
                                    if (StringUtils.startsWithIgnoreCase(it.getName(), testClzName)) {
                                        return true;
                                    }

                                    if (it.getType().contains(testClzName)) {
                                        return true;
                                    }

                                    // 描述 中提到了下文标题
                                    if (StringUtils.contains(it.getDesc(), testClzName)) {
                                        final DocField docField = new DocField();
                                        it.getChildren().add(docField);

                                        docField.setName(testClzName);
                                        docField.setType("object");
                                        objectTypeDocFieldMap.put(docField, docField);

                                        return true;
                                    }

                                    return false;
                                })
                );

                if (clzName == null) {
                    throw new IllegalArgumentException("Can't find the right `clzName` in heading or other node " + next.getChars());
                }

                objectTypeDocFieldMap.values()
                        .stream()
                        .filter(it -> it.getType().contains(clzName) || StringUtils.equals(it.getName(), clzName))
                        .forEach(docField -> {
                            final List<DocField> children = docField.getChildren();
                            if (CollectionUtils.isEmpty(children)) {
                                docField.setChildren(docFields);
                            } else if (!children.equals(docFields)) {
                                docField.addSchema(docFields);
                            }
                        });

                docFields.forEach(docField ->
                        Optional.ofNullable(objectTypeDocFieldMap.get(docField))
                                .ifPresent(it -> docField.setChildren(it.getChildren()))
                );
            }
        }

        return res;
    }

    protected void parseResponse(Node node) {
        HtmlBlock htmlBlock = (HtmlBlock) node.getNextAny(HtmlBlock.class);
        final List<DocField> respFields;
        if (htmlBlock != null) {
            respFields = parseTable(htmlBlock);
        } else {
            Document document = Jsoup.parse(node.getNextAny().getChars().toString());
            respFields = parseTableOrData(document);
        }
        builder.respFields(respFields);
    }

    protected void parseResponseExample(Node node) {
        builder.responseJson(node.getNext().getChildChars().toString());
    }

    protected void parseErrorResponseExample(Node node) {
        builder.errorResponseJson(node.getNext().getChildChars().unescape());
    }

    @SneakyThrows
    public GeneratorContent parse() {

        final List<Consumer<Paragraph>> paragraphConsumers = Lists.newArrayList(text -> {
                    if (text.getLineCount() != 2) {
                        return;
                    }
                    for (final BasedSequence segment : text.getContentLines()) {
                        final Document document = Jsoup.parse(segment.toString());
                        final Elements customHeading = document.select("customheading");
                        if (customHeading.isEmpty()) {

                            final Elements hrefE = document.getElementsByAttribute("href");
                            String desc = document.body().html();
                            if (!hrefE.isEmpty()) {
                                final String href = hrefE.attr("href");
                                desc = desc.replace(href, Misc.DOC_BASE_URL + href);
                            }
                            builder.desc(desc);
                            return;
                        }
                    }
                }
        );


        markdown.getChildIterator().forEachRemaining(node -> {
            if (node instanceof Heading) {
                consumers.forEach(it -> it.accept((Heading) node));
            } else if (node instanceof Paragraph) {
                paragraphConsumers.forEach(it -> it.accept((Paragraph) node));
            }
        });

        return builder.build();
    }

    static List<DocField> parseHead(Document document) {
        Elements elements = document.select("li");
        if (elements.isEmpty()) {
            final List<DocField> docFields = tableToDocFields(document);
            if (!docFields.isEmpty()) {
                return docFields;
            }
        }

        return parseHead(elements.eachText());
    }

    static List<DocField> parseHead(List<String> textList) {
        return textList
                .stream()
                .map(it -> {
                    final DocField docField = new DocField();

                    Stream.of(":", "：")
                            .filter(it::contains)
                            .map(splitter -> it.split(splitter, 2))
                            .forEach(array -> {
                                final String name = array[0];
                                final String value = array[1];
                                final BiConsumer<DocField, String> setter = DocField.getByColumnName(name);
                                if (setter != null) {
                                    setter.accept(docField, value);
                                } else {
                                    docField.setName(name);
                                    docField.setDesc(value);
                                    docField.setType("string");
                                }
                            });


                    return docField;
                })
                .collect(Collectors.toList());
    }

    List<DocField> parseTableOrData(Element element) {
        final Elements data = element.getElementsByAttribute("data");

        if (!data.isEmpty()) {
            final String dataJson = data.first().attr("data");

            return Stream.of("fields", "data")
                    .filter(it -> dataJson.startsWith("{\"" + it))
                    .findFirst()
                    .map(it -> JsonPath.parse(dataJson).read(it, DocField[].class))
                    .map(Arrays::asList)
                    .orElse(Collections.emptyList());
        }

        return tableToDocFields(element);
    }

    static int calculateColspan(List<Element> elements) {
        return elements.stream()
                .map(it -> it.attr("colspan"))
                .mapToInt(Integer::parseInt)
                .sum();
    }

    static RangeMap<Integer, String> toRangeMetadata(Element metadata) {

        final Elements elements = metadata.children();

        final ImmutableRangeMap.Builder<Integer, String> builder = ImmutableRangeMap.builder();

        int start = 0, end = 1;
        String column = elements.get(start).text();
        final int size = elements.size();
        final int maxIndex = size - 1;

        for (; end <= maxIndex; end++) {
            final Element element = elements.get(end);
            String nextColumn = element.text();
            if (!nextColumn.isEmpty()) {
                final String colspan = element.attr("colspan");
                final int lower, upper;
                if (StringUtils.isNotBlank(colspan)) {
                    lower = calculateColspan(elements.subList(0, start));
                    upper = lower + calculateColspan(elements.subList(start, end));
                } else {
                    lower = start;
                    upper = end;
                }

                if (lower == upper) {
                    builder.put(Range.singleton(lower), column);
                } else {
                    builder.put(Range.closedOpen(lower, upper), column);
                }

                start = end;
                column = nextColumn;

                if (end == maxIndex) {
                    builder.put(Range.atLeast(upper), column);
                }
            }
        }

        return builder.build();
    }

    static List<DocField> tableToDocFields(Element document) {

        final Elements elements = document.select("tr");

        if (elements.isEmpty()) {
//            log.warn("missing `tr`, {}", document);
            return Collections.emptyList();
        }

        final Element metadata = Objects.requireNonNull(elements.first(), "table 缺少 头信息");

        final RangeMap<Integer, String> rangeMap = toRangeMetadata(metadata);

        final List<String> columns = metadata.children().eachText();

        final Map<Integer, DocField> lastParentColumn = Maps.newTreeMap(Comparator.<Integer>comparingInt(k -> k).reversed());

        return elements.next()
                .stream()
                .map(Element::children)
                .map(values -> {
                    final DocField docField = new DocField();

                    int nameIndex = 0, colspanSum = 0;

                    for (int i = 0; i < values.size(); i++) {
                        final Element element = values.get(i);
                        final String value = element.text();

                        final boolean hasColspan = element.hasAttr("colspan");
                        if (hasColspan) {
                            colspanSum += calculateColspan(Collections.singletonList(element));
                        }

                        if (StringUtils.isBlank(value)) {
                            continue;
                        }

                        final int index = hasColspan ? colspanSum - 1 : i;

                        String column = rangeMap.get(index);
                        if (StringUtils.isBlank(column)) {
                            column = i == 1 ? columns.get(0) : Lists.reverse(columns.subList(0, i))
                                    .stream()
                                    .filter(StringUtils::isNotBlank)
                                    .findFirst()
                                    .orElse("");
                        }
                        final BiConsumer<DocField, String> setter = DocField.getByColumnName(column);
                        if (setter != null) {
                            final String name = docField.getName();
                            setter.accept(docField, value);

                            if (StringUtils.isEmpty(name) && StringUtils.isNotEmpty(docField.getName())) {
                                nameIndex = i;
                            }
                        }

                    }

                    final String name = docField.getName();
                    if (StringUtils.isBlank(name) || StringUtils.equalsAny(name, ".", "-")) {
                        // 忽略一条空数据
                        return null;
                    }

                    final String levelKey = name.startsWith(".") ? "." : (name.startsWith("-") ? "-" : null);
                    if (levelKey != null) {
                        final int dotCount = StringUtils.countMatches(name, levelKey.charAt(0));
                        nameIndex = dotCount;
                        docField.setName(name.substring(dotCount).trim());
                    }

                    final int finalNameIndex = nameIndex;
                    lastParentColumn.entrySet()
                            .stream()
                            .filter(entry -> entry.getKey() < finalNameIndex)
                            .findFirst()
                            .map(Entry::getValue)
                            .ifPresent(parent -> {
                                docField.setParent(parent);
                                parent.getChildren().add(docField);
                            });

                    if (docField.isObjectType() || docField.isArrayObject()) {
                        lastParentColumn.put(nameIndex, docField);
                    }

                    return nameIndex == 0 ? docField : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
