/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeYamlPropertyConditionally extends Recipe {

    @Value
    public static class Condition {
        @Option(displayName = "JsonPath",
                description = "JsonPath to the property to check.",
                example = "$.kind")
        String jsonPath;

        @Option(displayName = "Value",
                description = "The value that the property must equal.",
                example = "Kustomization")
        String value;
    }

    @Option(displayName = "Conditions",
            description = "List of conditions that must ALL match (AND logic) for the update to occur.",
            example = "[{\"jsonPath\": \"$.kind\", \"value\": \"Kustomization\"}]")
    List<Condition> conditions;

    @Option(displayName = "Target JsonPath",
            description = "JsonPath to the property to update.",
            example = "$.spec.replicas")
    String targetJsonPath;

    @Option(displayName = "Old value",
            description = "Only change the property value if it matches the configured `oldValue`.",
            required = false,
            example = "oci://[^:]+:[0-9.]+")
    @Nullable
    String oldValue;

    @Option(displayName = "New value",
            description = "The new value to set. Use `$1`, `$2`, etc. for capture group references when `regex` is enabled.",
            example = "$1:2025.4.0")
    String newValue;

    @Option(displayName = "Regex",
            description = "Default `false`. If enabled, `oldValue` will be interpreted as a Regular Expression, " +
                          "and replacement will use `replaceAll` to substitute matched portions. " +
                          "Capture groups can be referenced in `newValue` using `$1`, `$2`, etc.",
            required = false)
    @Nullable
    Boolean regex;

    @Option(displayName = "File pattern",
            description = "A glob expression representing a file path to search for (relative to the project root). Blank/null matches all.",
            required = false,
            example = "**/k8s/**/*.yaml")
    @Nullable
    String filePattern;

    @Override
    public String getDisplayName() {
        return "Change YAML property conditionally";
    }

    @Override
    public String getDescription() {
        return "Updates a YAML property value only when all specified conditions in the same document match. " +
                "Supports multiple conditions (AND logic) and regex-based replacement with capture groups. " +
                "Useful for updating values in multi-document YAML files where each document " +
                "should be evaluated independently.";
    }

    @Override
    public String getInstanceNameSuffix() {
        return String.format("`%s` to `%s` when %d condition(s) match",
                targetJsonPath, newValue, conditions != null ? conditions.size() : 0);
    }

    @Override
    public Validated<Object> validate() {
        return super.validate().and(
                Validated.test("oldValue", "is required if `regex` is enabled", oldValue,
                        value -> !(Boolean.TRUE.equals(regex) && StringUtils.isNullOrEmpty(value))));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JsonPathMatcher targetMatcher = new JsonPathMatcher(targetJsonPath);

        // Pre-compile condition matchers - use path string as key since JsonPathMatcher
        // doesn't implement equals/hashCode properly
        Map<String, JsonPathMatcher> conditionPathMatchers = new HashMap<>();
        Map<String, String> conditionExpectedValues = new HashMap<>();
        if (conditions != null) {
            for (Condition condition : conditions) {
                String path = condition.getJsonPath();
                conditionPathMatchers.put(path, new JsonPathMatcher(path));
                conditionExpectedValues.put(path, condition.getValue());
            }
        }

        return Preconditions.check(new FindSourceFiles(filePattern), new YamlIsoVisitor<ExecutionContext>() {

            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                // Check if this document meets ALL conditions
                if (!allConditionsMet(document, conditionPathMatchers, conditionExpectedValues, ctx)) {
                    return document;
                }

                // All conditions met, update the target property
                Yaml.Document updated = (Yaml.Document) new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                        if (targetMatcher.matches(getCursor()) && matchesOldValue(e.getValue())) {
                            Yaml.Block updatedValue = updateValue(e.getValue());
                            if (updatedValue != null) {
                                e = e.withValue(updatedValue);
                            }
                        }
                        return e;
                    }
                }.visit(document, ctx);

                return updated != null ? updated : document;
            }

            private boolean allConditionsMet(Yaml.Document document,
                                             Map<String, JsonPathMatcher> pathMatchers,
                                             Map<String, String> expectedValues,
                                             ExecutionContext ctx) {
                if (pathMatchers.isEmpty()) {
                    return true;
                }

                Map<String, Boolean> matchResults = new HashMap<>();
                for (String path : pathMatchers.keySet()) {
                    matchResults.put(path, false);
                }

                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                        for (Map.Entry<String, JsonPathMatcher> matcherEntry : pathMatchers.entrySet()) {
                            String path = matcherEntry.getKey();
                            JsonPathMatcher matcher = matcherEntry.getValue();
                            String expectedValue = expectedValues.get(path);

                            if (matcher.matches(getCursor()) && entry.getValue() instanceof Yaml.Scalar) {
                                String actualValue = ((Yaml.Scalar) entry.getValue()).getValue();
                                if (expectedValue.equals(actualValue)) {
                                    matchResults.put(path, true);
                                }
                            }
                        }
                        return super.visitMappingEntry(entry, ctx);
                    }
                }.visit(document, ctx);

                return !matchResults.containsValue(false);
            }

            private boolean matchesOldValue(Yaml.Block value) {
                if (!(value instanceof Yaml.Scalar)) {
                    return false;
                }
                Yaml.Scalar scalar = (Yaml.Scalar) value;
                if (StringUtils.isNullOrEmpty(oldValue)) {
                    return true;
                }
                return Boolean.TRUE.equals(regex) ?
                        Pattern.compile(oldValue).matcher(scalar.getValue()).find() :
                        scalar.getValue().equals(oldValue);
            }

            // Returns null if value should not change
            private Yaml.@Nullable Block updateValue(Yaml.Block value) {
                if (!(value instanceof Yaml.Scalar)) {
                    return null;
                }
                Yaml.Scalar scalar = (Yaml.Scalar) value;
                String updatedValue = Boolean.TRUE.equals(regex) ?
                        scalar.getValue().replaceAll(oldValue, newValue) :
                        newValue;
                return scalar.getValue().equals(updatedValue) ? null : scalar.withValue(updatedValue);
            }
        });
    }
}
