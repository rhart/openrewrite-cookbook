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

 import java.util.concurrent.atomic.AtomicBoolean;

 /**
  * Updates a YAML property value only when another property in the same document matches a
  condition.
  * Handles multi-document YAML files correctly, evaluating each document independently.
  */
 @NullMarked
 @Value
 @EqualsAndHashCode(callSuper = false)
 public class ChangeYamlPropertyConditionally extends Recipe {

     @Option(displayName = "Condition JsonPath",
             description = "JsonPath to the property that must match for the update to occur.",
             example = "$.metadata.labels.environment")
     String conditionJsonPath;

     @Option(displayName = "Condition value",
             description = "The value that conditionJsonPath must equal for the update to occur.",
             example = "production")
     String conditionValue;

     @Option(displayName = "Target JsonPath",
             description = "JsonPath to the property to update.",
             example = "$.spec.replicas")
     String targetJsonPath;

     @Option(displayName = "New value",
             description = "The new value to set at targetJsonPath.",
             example = "3")
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
         return "Updates a YAML property value only when another property in the same document " +
                 "matches a specified condition. " +
                 "Useful for updating values in multi-document YAML files where each document " +
                 "should be evaluated independently.";
     }

     @Override
     public String getInstanceNameSuffix() {
         return String.format("`%s` to `%s` when `%s` = `%s`", targetJsonPath, newValue,
                 conditionJsonPath, conditionValue);
     }

     @Override
     public TreeVisitor<?, ExecutionContext> getVisitor() {
         JsonPathMatcher conditionMatcher = new JsonPathMatcher(conditionJsonPath);
         JsonPathMatcher targetMatcher = new JsonPathMatcher(targetJsonPath);

         return Preconditions.check(new FindSourceFiles(filePattern), new
                 YamlIsoVisitor<ExecutionContext>() {

                     @Override
                     public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                         // Check if this document meets the condition
                         AtomicBoolean conditionMet = new AtomicBoolean(false);

                         new YamlIsoVisitor<ExecutionContext>() {
                             @Override
                             public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry,
                                                                         ExecutionContext ctx) {
                                 if (conditionMatcher.matches(getCursor())) {
                                     if (entry.getValue() instanceof Yaml.Scalar) {
                                         String value = ((Yaml.Scalar) entry.getValue()).getValue();
                                         if (conditionValue.equals(value)) {
                                             conditionMet.set(true);
                                         }
                                     }
                                 }
                                 return super.visitMappingEntry(entry, ctx);
                             }
                         }.visit(document, ctx);

                         // If condition is met, update the target property
                         if (conditionMet.get()) {
                             Yaml.Document updated = (Yaml.Document) new YamlIsoVisitor<ExecutionContext>() {
                                 @Override
                                 public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry,
                                                                             ExecutionContext ctx) {
                                     Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                                     if (targetMatcher.matches(getCursor())) {
                                         if (e.getValue() instanceof Yaml.Scalar) {
                                             Yaml.Scalar scalar = (Yaml.Scalar) e.getValue();
                                             if (!newValue.equals(scalar.getValue())) {
                                                 e = e.withValue(scalar.withValue(newValue));
                                             }
                                         }
                                     }
                                     return e;
                                 }
                             }.visit(document, ctx);
                             return updated != null ? updated : document;
                         }

                         return document;
                     }
                 });
     }
 }
