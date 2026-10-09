/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anacoders.cookbook.yaml;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads a property from a YAML document that is embedded in a scalar (for example a Kustomize or Flux
 * {@code patch} block scalar) and writes it to a property of the enclosing document.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class CopyEmbeddedYamlValue extends Recipe {

    @Option(displayName = "Source key path",
            description = "A [JsonPath](https://docs.openrewrite.org/reference/jsonpath-and-jsonpathmatcher-reference) expression " +
                    "matching the scalar(s) whose value is an embedded YAML document. The first match that contains `sourceProperty` is used.",
            example = "$.spec.patches[*].patch")
    String sourceKeyPath;

    @Option(displayName = "Source property",
            description = "The property to read inside the embedded YAML document, in dot notation.",
            example = "spec.values.ingress.host")
    String sourceProperty;

    @Option(displayName = "Property key",
            description = "The property of the enclosing document to write the value to, in dot notation. " +
                    "Missing keys are created; an existing value is replaced. The value is written as a double-quoted string.",
            example = "spec.postBuild.substitute.ingressHost")
    String propertyKey;

    @Option(displayName = "File pattern",
            description = "A glob expression representing a file path to search for (relative to the project root). Blank/null matches all.",
            required = false,
            example = "**/*.yaml")
    @Nullable
    String filePattern;

    @Override
    public String getDisplayName() {
        return "Copy a value out of embedded YAML";
    }

    @Override
    public String getInstanceNameSuffix() {
        return String.format("`%s` from `%s` to `%s`", sourceProperty, sourceKeyPath, propertyKey);
    }

    @Override
    public String getDescription() {
        return "Reads a property from the YAML document embedded in a scalar, such as a Flux Kustomization `patch` block scalar, " +
                "and writes it to a property of the enclosing document. Does nothing when no matching scalar holds the property.";
    }

    @Override
    public Validated<Object> validate() {
        return super.validate().and(JsonPathMatcher.validate("sourceKeyPath", sourceKeyPath));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(filePattern), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                String value = findEmbeddedValue(document, ctx);
                if (value == null) {
                    return document;
                }
                Yaml.Block incoming = parseBlock(snippet(value), ctx);
                if (incoming == null) {
                    return document;
                }
                Yaml.Block merged = (Yaml.Block) new MergeYamlVisitor<>(document.getBlock(), incoming, false, null, null, null)
                        .visitNonNull(document.getBlock(), ctx, getCursor());
                return document.withBlock(merged);
            }

            private @Nullable String findEmbeddedValue(Yaml.Document document, ExecutionContext ctx) {
                JsonPathMatcher matcher = new JsonPathMatcher(sourceKeyPath);
                AtomicReference<String> found = new AtomicReference<>();
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (found.get() == null && entry.getValue() instanceof Yaml.Scalar && matcher.matches(getCursor())) {
                            Yaml.Block embedded = parseBlock(((Yaml.Scalar) entry.getValue()).getValue(), ctx);
                            if (embedded != null) {
                                String value = findProperty(embedded, ctx);
                                if (value != null) {
                                    found.set(value);
                                }
                            }
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }
                }.visit(document, ctx, getCursor().getParentOrThrow());
                return found.get();
            }

            private @Nullable String findProperty(Yaml.Block embedded, ExecutionContext ctx) {
                AtomicReference<String> found = new AtomicReference<>();
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (found.get() == null && entry.getValue() instanceof Yaml.Scalar && sourceProperty.equals(dottedKey(getCursor()))) {
                            found.set(((Yaml.Scalar) entry.getValue()).getValue());
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }
                }.visit(embedded, ctx);
                return found.get();
            }
        });
    }

    private static Yaml.@Nullable Block parseBlock(String yaml, ExecutionContext ctx) {
        return YamlParser.builder().build().parse(ctx, yaml)
                .filter(Yaml.Documents.class::isInstance)
                .map(Yaml.Documents.class::cast)
                .filter(docs -> !docs.getDocuments().isEmpty())
                .map(docs -> docs.getDocuments().get(0).getBlock())
                .findFirst()
                .orElse(null);
    }

    private String snippet(String value) {
        String[] segments = propertyKey.split("\\.");
        StringBuilder yaml = new StringBuilder();
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            yaml.append(indent).append(segments[i]).append(':');
            if (i == segments.length - 1) {
                yaml.append(" \"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
            yaml.append('\n');
            indent.append("  ");
        }
        return yaml.toString();
    }

    private static String dottedKey(Cursor cursor) {
        StringBuilder key = new StringBuilder();
        Iterator<Object> path = cursor.getPath();
        while (path.hasNext()) {
            Object next = path.next();
            if (next instanceof Yaml.Mapping.Entry) {
                if (key.length() > 0) {
                    key.insert(0, '.');
                }
                key.insert(0, ((Yaml.Mapping.Entry) next).getKey().getValue());
            }
        }
        return key.toString();
    }
}
