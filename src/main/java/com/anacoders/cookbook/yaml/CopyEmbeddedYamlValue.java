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
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.trait.BlockScalar;
import org.openrewrite.yaml.tree.Yaml;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads a property from the YAML text stored inside a string value (for example a Flux Kustomization
 * {@code patch} block) and writes it to a property of the enclosing document.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class CopyEmbeddedYamlValue extends Recipe {

    private static final YamlParser PARSER = YamlParser.builder().build();

    @Option(displayName = "Source key path",
            description = "A [JsonPath](https://docs.openrewrite.org/reference/jsonpath-and-jsonpathmatcher-reference) expression " +
                    "matching the mapping value(s) whose text is a YAML document. The first match that contains `sourceProperty` is used.",
            example = "$.spec.patches[*].patch")
    String sourceKeyPath;

    @Option(displayName = "Source property",
            description = "The property to read inside the embedded YAML document, in dot notation. " +
                    "Only plain, single-quoted and double-quoted values are copied.",
            example = "spec.values.ingress.host")
    String sourceProperty;

    @Option(displayName = "Property key",
            description = "The property of the enclosing document to write the value to, in dot notation. " +
                    "Missing keys are created; an existing scalar value is replaced. The value is written as a double-quoted string.",
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
        return "Reads a property from the YAML text stored inside a string value, for example a Flux Kustomization `patch` block, " +
                "and writes it to a property of the enclosing document. Does nothing when no matching value holds the property.";
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
                String quotedValue = findEmbeddedValue(document, ctx);
                if (quotedValue == null) {
                    return document;
                }
                Yaml.Block incoming = parseBlock(snippet(quotedValue));
                if (incoming == null) {
                    return document;
                }
                return (Yaml.Document) new MergeYamlVisitor<>(document.getBlock(), incoming, false, null, null, null)
                        .visitNonNull(document, ctx, getCursor().getParentOrThrow());
            }

            private @Nullable String findEmbeddedValue(Yaml.Document document, ExecutionContext ctx) {
                JsonPathMatcher matcher = new JsonPathMatcher(sourceKeyPath);
                AtomicReference<String> found = new AtomicReference<>();
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (found.get() == null && entry.getValue() instanceof Yaml.Scalar && matcher.matches(getCursor())) {
                            Yaml.Block embedded = parseBlock(embeddedText((Yaml.Scalar) entry.getValue()));
                            if (embedded != null) {
                                found.set(findProperty(embedded, ctx));
                            }
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }

                    private String embeddedText(Yaml.Scalar scalar) {
                        return new BlockScalar.Matcher().get(new Cursor(getCursor(), scalar))
                                .map(BlockScalar::getBody)
                                .orElse(scalar.getValue());
                    }
                }.visit(document, ctx, getCursor().getParentOrThrow());
                return found.get();
            }

            private @Nullable String findProperty(Yaml.Block embedded, ExecutionContext ctx) {
                AtomicReference<String> found = new AtomicReference<>();
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        if (found.get() == null && entry.getValue() instanceof Yaml.Scalar &&
                                FindProperty.matches(getCursor(), sourceProperty, false)) {
                            found.set(doubleQuoted((Yaml.Scalar) entry.getValue()));
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }
                }.visit(embedded, ctx);
                return found.get();
            }
        });
    }

    /**
     * Parses YAML text on its own, so a malformed patch is skipped instead of being reported as a recipe error.
     */
    private static Yaml.@Nullable Block parseBlock(String yaml) {
        return PARSER.parse(new InMemoryExecutionContext(t -> {
                }), yaml)
                .filter(Yaml.Documents.class::isInstance)
                .map(Yaml.Documents.class::cast)
                .filter(docs -> !docs.getDocuments().isEmpty())
                .map(docs -> docs.getDocuments().get(0).getBlock())
                .findFirst()
                .orElse(null);
    }

    private static @Nullable String doubleQuoted(Yaml.Scalar scalar) {
        switch (scalar.getStyle()) {
            case PLAIN:
                return '"' + escape(scalar.getValue()) + '"';
            case SINGLE_QUOTED:
                return '"' + escape(scalar.getValue().replace("''", "'")) + '"';
            case DOUBLE_QUOTED:
                return '"' + scalar.getValue() + '"';
            default:
                return null;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String snippet(String quotedValue) {
        String[] segments = propertyKey.split("\\.");
        StringBuilder yaml = new StringBuilder();
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            yaml.append(indent).append(segments[i]).append(':');
            if (i == segments.length - 1) {
                yaml.append(' ').append(quotedValue);
            }
            yaml.append('\n');
            indent.append("  ");
        }
        return yaml.toString();
    }
}
