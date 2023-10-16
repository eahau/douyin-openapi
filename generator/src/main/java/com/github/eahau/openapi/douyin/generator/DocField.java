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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
public class DocField {

    /**
     * 字段名称.
     */
    @SerializedName(value = "name", alternate = {"key"})
    private String name = "";

    /**
     * 字段类型.
     */
    private String type;

    public boolean isBinaryType() {
        return StringUtils.equalsAny(getType(), "binary", "form-data");
    }

    public String getType() {
        if (this.type != null) {
            return this.type;
        }
        if (StringUtils.equalsAnyIgnoreCase(getName(), "content-type", "access-token")) {
            setType("string");
            return "string";
        }

        return Stream.of(getDefV(), getExample())
                .filter(Objects::nonNull)
                .map(v -> {
                    String type;
                    if (NumberUtils.isCreatable(v)) {
                        type = "number";
                    } else if (StringUtils.equalsAnyIgnoreCase(v, "true", "false")) {
                        type = "bool";
                    } else {
                        type = "string";
                    }

                    setType(type);
                    return type;
                })
                .findFirst()
                .orElse("struct");
    }

    /**
     * 是否必传字段.
     */
    private boolean required;

    /**
     * 字段描述信息.
     */
    private String desc;

    /**
     * 字段默认值.
     */
    private String defV;

    /**
     * 最大长度.
     */
    private String maxLength;

    /**
     * 示例值.
     */
    private String example;

    @SerializedName(value = "children", alternate = {"fields"})
    private List<DocField> children = Lists.newLinkedList();

    private Map<String, List<DocField>> otherSchemas = Maps.newHashMap();

    public void addSchema(List<DocField> children) {
        final Map<String, List<DocField>> otherSchemas = getOtherSchemas();
        otherSchemas.put(getName() + (otherSchemas.size() + 1), children);
    }

    public List<DocField> flatChildren() {
        final List<DocField> children = getChildren();

        if (CollectionUtils.isEmpty(children)) {
            return Collections.emptyList();
        }

        final List<DocField> list = Lists.newLinkedList();

        for (final DocField docField : children) {
            if (CollectionUtils.isEmpty(docField.getChildren())) {
                list.add(docField);
            } else {
                list.addAll(docField.flatChildren());
            }
        }

        return list;
    }

    private DocField parent;

    public DocField getParent() {
        DocField parent = this.parent;

        while (parent != null) {
            if (parent.isObjectType()) {
                break;
            }
            parent = parent.getParent();
        }

        return parent;
    }

    public boolean isRootObject() {
        return getParent() == null;
    }

    public String getParentName() {
        DocField parent = getParent();
        if (parent == null) {
            return "";
        }

        final LinkedList<String> list = Lists.newLinkedList();
        while (parent != null) {
            String name = parent.getName();
            if (parent.getType().startsWith("[]")) {
                name += "[0]";
            }
            list.addFirst(name);
            parent = parent.getParent();
        }

        return String.join(".", list);
    }

    public String getFullName() {
        String name = getName();
        if (getType().startsWith("[]")) {
            name += "[0]";
        }
        return String.join(".", getParentName(), name);
    }

    public void setDesc(String value) {
        this.desc = value;
        if (StringUtils.equalsAnyIgnoreCase(getName(), "content-type")) {
            if (StringUtils.containsIgnoreCase(value, "application/json")) {
                setDefV("application/json");
                setType("string");
            }
        } else {
            Stream.<Pair<String, Consumer<String>>>of(
                    Pair.of("示例", this::setDesc),
                    Pair.of(Misc.DEFAULT_VALUE_KEY, this::setDefV)
            )
                    .forEach(pair -> {
                                final String k = pair.getKey();
                                final String key = k + "：";

                                final String regex = value.contains(key) ? key : (value.contains(k) ? k : null);
                                if (regex == null) {
                                    return;
                                }
                                Arrays.stream(value.split(regex))
                                        .filter(StringUtils::isNotBlank)
                                        .map(it -> it.replace("\"", "").trim())
                                        .findFirst()
                                        .ifPresent(pair.getValue());
                            }
                    );

            if (StringUtils.equalsAny(getName(), "msg") && getType().equals("string")) {
                if (StringUtils.containsIgnoreCase(value, "json")) {
                    /**
                     * @see #isObjectType(String)
                     */
                    setType("Json Object");
                }
            }
        }
    }

    public boolean isObjectType() {
        final String type = getType();
        return isObjectType(type) || !isPrimitiveType(type);
    }

    private static boolean isObjectType(String type) {
        return StringUtils.equalsAny(type, "object", "struct", "strcut", "Json Object");
    }

    public boolean isArrayType() {
        return StringUtils.containsAnyIgnoreCase(getType(), "[]", "list", "array");
    }

    public boolean isMapType() {
        return StringUtils.startsWithIgnoreCase(getType(), "map");
    }

    public boolean isArrayObject() {
        return isArrayType() && isObjectType(getLastParamType());
    }

    public boolean isArrayOrObject() {
        return isArrayType() || isObjectType();
    }

    private static final ImmutableMap<Predicate<String>, BiConsumer<DocField, String>> setters
            = ImmutableMap.<Predicate<String>, BiConsumer<DocField, String>>builder()
            .put(it -> StringUtils.equalsAny(it, "", "参数", "属性", "参数名", "字段名")
                            || StringUtils.containsAny(it, "名称"),
                    DocField::setName)
            .put(it -> it.contains("类型"), DocField::setType)
            // 必须 必需 必填 必传
            .put(it -> it.contains("必"), (it, v) -> it.setRequired("是".equals(v) || "true".equalsIgnoreCase(v)))
            .put(it -> "说明".equals(it) || StringUtils.containsAny(it, "描述", "备注"), DocField::setDesc)
            .put(it -> it.contains("示例"), DocField::setExample)
            .put("默认值"::equals, DocField::setDefV)
            .put("最大长度"::equals, DocField::setMaxLength)
            .build();

    public static BiConsumer<DocField, String> getByColumnName(String name) {
        for (final Entry<Predicate<String>, BiConsumer<DocField, String>> entry : setters.entrySet()) {
            if (entry.getKey().test(name)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public boolean isUnknownTypeArray() {
        return StringUtils.equalsAnyIgnoreCase(getType(), "[]", "list", "array");
    }

    public Parameter toParameter(Supplier<Parameter> supplier) {
        return supplier.get()
                .name(getName())
                .description(getDesc())
                .example(getExample())
                .required(isRequired())
                .schema(toSchema());
    }

    private Schema schema;

    Schema toPrimitiveSchema(String type) {
        final Schema schema = toPrimitiveSchemaOrNull(type);
        if (schema == null) {
            throw new IllegalArgumentException(getName() + " unknown type: " + type);
        }

        return schema;
    }

    boolean isPrimitiveType(String type) {
        return toPrimitiveSchemaOrNull(type) != null;
    }

    Schema toPrimitiveSchemaOrNull(String type) {
        final String defV = getDefV();
        if (type.contains("bool")) {
            return new BooleanSchema()._default(Boolean.parseBoolean(defV));
        }
        if (StringUtils.equalsAnyIgnoreCase(type, "sting", "string")) {
            return new StringSchema()._default(defV);
        }
        if (StringUtils.containsAny(type, "int", "i32")) {
            final IntegerSchema numberSchema = new IntegerSchema();
            if (NumberUtils.isCreatable(defV)) {
                numberSchema._default(NumberUtils.toInt(defV));
            }
            return numberSchema;
        }
        if (StringUtils.containsAny(type, "i64", "int64", "long")) {
            final IntegerSchema numberSchema = new IntegerSchema();
            numberSchema.type("integer").format("int64");
            if (NumberUtils.isCreatable(defV)) {
                numberSchema._default(NumberUtils.toLong(defV));
            }
            return numberSchema;
        }
        if (StringUtils.containsAny(type, "float", "double", "number")) {
            final Schema numberSchema = new Schema();
            numberSchema.type("number").format("double");
            if (NumberUtils.isCreatable(defV)) {
                numberSchema._default(NumberUtils.toDouble(defV));
            }
            return numberSchema;
        }
        if (isBinaryType()) {
            return new FileSchema();
        }

        return null;
    }

    String getLastParamType() {
        final String type = getType();
        StringTokenizer tokenizer = new StringTokenizer(type, "[]<>(),");

        String paramType = "object";
        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            if (!StringUtils.equalsAnyIgnoreCase(token, "list", "array")) {
                paramType = token;
            }
        }

        return paramType;
    }

    boolean isPrimitiveArrayType() {
        return isArrayType() && isPrimitiveType(getLastParamType());
    }

    Schema toObjectSchema() {
        final List<DocField> children = getChildren();
        final Schema schema;
        final DocField childField;
        if (children.size() == 1 && !(childField = children.get(0)).isObjectType() && !childField.isPrimitiveArrayType()) {
            schema = childField.toSchema();
        } else {
            final Map<String, Schema> properties = children
                    .stream()
                    .map(DocField::toSchema)
                    // https://developer.open-douyin.com/docs/resource/zh-CN/mini-app/develop/server/data-analysis/component-analysis/component-overview-analysis
                    // 离谱，竟然还有重名对象（字段数量不一样）所以用新的 schema(文档顺序)
                    .collect(Collectors.toMap(Schema::getName, Function.identity(), (oldV, newV) -> newV));
            schema = new ObjectSchema().properties(properties);
        }

        return schema;
    }

    public Schema<?> toSchema() {

        if (this.schema == null) {

            final String type = getType();

            final String defV = getDefV();
            if (isArrayObject()) {
                setSchema(new ArraySchema().items(toObjectSchema())._default(defV));
            } else if (isArrayType()) {
                final String paramType = getLastParamType();
                final Schema schema = Optional.ofNullable(toPrimitiveSchemaOrNull(paramType))
                        .orElseGet(this::toObjectSchema);
                setSchema(new ArraySchema().items(schema)._default(defV));
            } else if (isObjectType()) {
                setSchema(toObjectSchema()._default("{}".equals(defV) ? null : defV));
            } else if (isMapType()) {
                final String paramType = getLastParamType();
                final Schema schema = Optional.ofNullable(toPrimitiveSchemaOrNull(paramType))
                        .orElseGet(this::toObjectSchema);
                setSchema(new MapSchema().additionalProperties(schema)._default(defV));
            } else {
                setSchema(toPrimitiveSchema(type));
            }
            this.schema.name(getName()).example(getExample()).description(getDesc());
        }

        return schema;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof DocField) {
            return StringUtils.equals(getName(), ((DocField) obj).getName())
                    && StringUtils.equals(getType(), ((DocField) obj).getType());
        }
        return false;
    }

}
