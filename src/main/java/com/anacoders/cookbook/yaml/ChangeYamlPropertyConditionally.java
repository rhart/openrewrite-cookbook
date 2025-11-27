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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates a YAML property value only when all specified conditions in the same document match.
 * Handles multi-document YAML files correctly, evaluating each document independently.
 * Supports regex-based replacement with capture groups.
 */
@NullMarked
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

    @Option(displayName = "Old value pattern",
            description = "Optional regex pattern to match current value. Use capture groups for replacement. If not set, value is replaced unconditionally.",
            required = false,
            example = "(oci://[^:]+):[0-9.]+")
    @Nullable
    String oldValuePattern;

    @Option(displayName = "New value",
            description = "The new value to set at targetJsonPath. Use $1, $2 etc for capture groups if oldValuePattern is set.",
            example = "$1:2025.4.0")
    String newValue;

    @Option(displayName = "File pattern",
            description = "A glob expression for files to process. Blank/null matches all.",
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

        // Pre-compile regex pattern if provided
        Pattern valuePattern = oldValuePattern != null && !oldValuePattern.isEmpty()
                ? Pattern.compile(oldValuePattern)
                : null;

        return Preconditions.check(new FindSourceFiles(filePattern), new
                YamlIsoVisitor<ExecutionContext>() {

                    @Override
                    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                        // Check if this document meets ALL conditions
                        if (!allConditionsMet(document, conditionPathMatchers, conditionExpectedValues, ctx)) {
                            return document;
                        }

                        // All conditions met, update the target property
                        Yaml.Document updated = (Yaml.Document) new YamlIsoVisitor<ExecutionContext>() {
                            @Override
                            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry,
                                                                        ExecutionContext ctx) {
                                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                                if (targetMatcher.matches(getCursor())) {
                                    if (e.getValue() instanceof Yaml.Scalar) {
                                        Yaml.Scalar scalar = (Yaml.Scalar) e.getValue();
                                        String currentValue = scalar.getValue();
                                        String replacementValue = computeNewValue(currentValue,
                                                valuePattern);

                                        if (replacementValue != null &&
                                                !replacementValue.equals(currentValue)) {
                                            e = e.withValue(scalar.withValue(replacementValue));
                                        }
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
                            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry,
                                                                        ExecutionContext ctx) {
                                for (Map.Entry<String, JsonPathMatcher> matcherEntry :
                                        pathMatchers.entrySet()) {
                                    String path = matcherEntry.getKey();
                                    JsonPathMatcher matcher = matcherEntry.getValue();
                                    String expectedValue = expectedValues.get(path);

                                    if (matcher.matches(getCursor())) {
                                        if (entry.getValue() instanceof Yaml.Scalar) {
                                            String actualValue = ((Yaml.Scalar)
                                                    entry.getValue()).getValue();
                                            if (expectedValue.equals(actualValue)) {
                                                matchResults.put(path, true);
                                            }
                                        }
                                    }
                                }
                                return super.visitMappingEntry(entry, ctx);
                            }
                        }.visit(document, ctx);

                        // All conditions must be met
                        return !matchResults.containsValue(false);
                    }

                    @Nullable
                    private String computeNewValue(String currentValue, @Nullable Pattern pattern) {
                        if (pattern == null) {
                            // No pattern, direct replacement
                            return newValue;
                        }

                        Matcher matcher = pattern.matcher(currentValue);
                        if (!matcher.matches()) {
                            // Pattern doesn't match, don't change
                            return null;
                        }

                        // Replace $1, $2, etc. with capture groups
                        String result = newValue;
                        for (int i = 1; i <= matcher.groupCount(); i++) {
                            String group = matcher.group(i);
                            if (group != null) {
                                result = result.replace("$" + i, group);
                            }
                        }
                        return result;
                    }
                });
    }
}
