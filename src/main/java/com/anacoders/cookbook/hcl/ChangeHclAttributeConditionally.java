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
package com.anacoders.cookbook.hcl;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.hcl.HclIsoVisitor;
import org.openrewrite.hcl.tree.Expression;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.internal.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeHclAttributeConditionally extends Recipe {

    @Value
    public static class CommentCondition {
        @Option(displayName = "Comment pattern",
                description = "The comment text pattern to match (supports partial matching).",
                example = "# infra.gradle.com/release-channel:")
        String pattern;

        @Option(displayName = "Comment value",
                description = "The value that must appear after the pattern in the comment.",
                example = "stable")
        String value;
    }

    @Option(displayName = "Attribute name",
            description = "The HCL attribute name to update.",
            example = "chart_version")
    String attributeName;

    @Option(displayName = "New value",
            description = "The new value to set for the attribute.",
            example = "2.0.0")
    String newValue;

    @Option(displayName = "Old value",
            required = false,
            description = "Only change the attribute value if it matches the configured `oldValue`.",
            example = "1.0.0")
    @Nullable
    String oldValue;

    @Option(displayName = "Regex",
            description = "Default `false`. If enabled, `oldValue` will be interpreted as a " +
            "Regular Expression, to replace only all parts that match the regex. Capturing group can be " +
            "used in `newValue`.",
            required = false)
    @Nullable
    Boolean regex;

    @Option(displayName = "Comment conditions",
            description = "A list of comment-based conditions that must ALL be met (AND logic) " +
                    "for the change to be made.",
            required = false,
            example = "[{\"pattern\": \"# release-channel:\", \"value\": \"stable\"}]")
    @Nullable
    List<CommentCondition> commentConditions;

    @Option(displayName = "File pattern",
            description = "A glob expression representing a file path to search for (relative to " +
            "the project root). Blank/null matches all.",
            required = false,
            example = "**/*.tf")
    @Nullable
    String filePattern;

    @Override
    public String getDisplayName() {
        return "Change HCL attribute conditionally";
    }

    @Override
    public String getInstanceNameSuffix() {
        return String.format("`%s` to `%s`", attributeName, newValue);
    }

    @Override
    public String getDescription() {
        return "Change an HCL attribute value based on comment-based conditions. " +
                "Useful for updating Terraform configurations conditionally.";
    }

    @Override
    public Validated<Object> validate() {
        return super.validate().and(
                Validated.test("oldValue", "is required if `regex` is enabled", oldValue,
                        value -> !(Boolean.TRUE.equals(regex) &&
                                StringUtils.isNullOrEmpty(value))));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(filePattern), new HclIsoVisitor<ExecutionContext>() {
            @Override
            public Hcl.Attribute visitAttribute(Hcl.Attribute attribute, ExecutionContext ctx) {
                Hcl.Attribute a = super.visitAttribute(attribute, ctx);

                // Check if attribute name matches
                if (!(a.getName() instanceof Hcl.Identifier)) {
                    return a;
                }
                if (!attributeName.equals(((Hcl.Identifier) a.getName()).getName())) {
                    return a;
                }

                // Check if comment conditions are met (if any)
                if (commentConditions != null && !commentConditions.isEmpty()) {
                    Hcl.Block block = getCursor().firstEnclosing(Hcl.Block.class);
                    if (block == null || !hasMatchingComments(block)) {
                        return a;
                    }
                }

                // Update the value
                Expression updatedValue = updateValue(a.getValue());
                return updatedValue != null ? a.withValue(updatedValue) : a;
            }
        });
    }

    private @Nullable Expression updateValue(Expression value) {
        // Handle QuotedTemplate (e.g., "value")
        if (value instanceof Hcl.QuotedTemplate) {
            Hcl.QuotedTemplate qt = (Hcl.QuotedTemplate) value;
            if (qt.getExpressions().size() == 1 && qt.getExpressions().get(0) instanceof Hcl.Literal) {
                Hcl.Literal literal = (Hcl.Literal) qt.getExpressions().get(0);
                Hcl.Literal updated = updateLiteral(literal);
                if (updated != null) {
                    List<Expression> exprs = new ArrayList<>();
                    exprs.add(updated);
                    return qt.withExpressions(exprs);
                }
            }
            return null;
        }
        // Handle bare Literal
        if (value instanceof Hcl.Literal) {
            return updateLiteral((Hcl.Literal) value);
        }
        return null;
    }

    private Hcl.@Nullable Literal updateLiteral(Hcl.Literal literal) {
        if (literal.getValue() == null) {
            return null;
        }

        String currentValue = literal.getValue().toString();
        String replacementValue;

        if (oldValue != null && !oldValue.isEmpty()) {
            if (Boolean.TRUE.equals(regex)) {
                if (!currentValue.matches(oldValue)) {
                    return null;
                }
                replacementValue = currentValue.replaceAll(oldValue, newValue);
            } else {
                if (!oldValue.equals(currentValue)) {
                    return null;
                }
                replacementValue = newValue;
            }
        } else {
            replacementValue = newValue;
        }

        if (replacementValue.equals(currentValue)) {
            return null;
        }

        String currentValueSource = literal.getValueSource();
        boolean isQuoted = currentValueSource.startsWith("\"") && currentValueSource.endsWith("\"");
        String newValueSource = isQuoted ? "\"" + replacementValue + "\"" : replacementValue;
        return literal.withValue(replacementValue).withValueSource(newValueSource);
    }

    private boolean hasMatchingComments(Hcl.Block block) {
        StringBuilder allText = new StringBuilder();

        // Get comments from the block prefix
        for (org.openrewrite.hcl.tree.Comment comment : block.getPrefix().getComments()) {
            allText.append(comment.getText());
        }

        // Check all body elements' comments
        for (Hcl content : block.getBody()) {
            for (org.openrewrite.hcl.tree.Comment comment : content.getPrefix().getComments()) {
                allText.append(comment.getText());
            }
        }

        String searchText = allText.toString();

        // Check if all comment conditions are met
        for (CommentCondition condition : commentConditions) {
            // Comment text doesn't include the # marker, so strip it from the pattern
            String pattern = condition.getPattern();
            if (pattern.startsWith("#")) {
                pattern = pattern.substring(1).trim();
            } else if (pattern.startsWith("//")) {
                pattern = pattern.substring(2).trim();
            }
            String expectedText = pattern + " " + condition.getValue();

            if (!searchText.contains(expectedText)) {
                return false;
            }
        }

        return true;
    }
}
