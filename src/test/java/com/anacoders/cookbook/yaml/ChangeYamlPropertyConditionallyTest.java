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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeYamlPropertyConditionallyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeYamlPropertyConditionally(
          "spec.replicas",
          "3",
          null,
          null,
          null,
          null,
          null
        ));
    }

    @DocumentExample
    @Test
    void updatesPropertyValue() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            spec:
              replicas: 1
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            spec:
              replicas: 3
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenValueAlreadyMatches() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            spec:
              replicas: 3
            """
          )
        );
    }

    @Test
    void updatesWithOldValueMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            "1",
            null,
            null,
            null,
            null
          )),
          yaml(
            """
            spec:
              replicas: 1
            """,
            """
            spec:
              replicas: 5
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenOldValueDoesNotMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            "1",
            null,
            null,
            null,
            null
          )),
          yaml(
            """
            spec:
              replicas: 2
            """
          )
        );
    }

    @Test
    void replacesUsingRegex() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.image",
            "$1:2.0.0",
            "([^:]+):[0-9.]+",
            true,
            null,
            null,
            null
          )),
          yaml(
            """
            spec:
              image: nginx:1.0.0
            """,
            """
            spec:
              image: nginx:2.0.0
            """
          )
        );
    }

    @Test
    void replacesUsingRegexWithMultipleCaptureGroups() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.path",
            "$1v2.0.0$2",
            "(.*/)v[0-9]+\\.[0-9]+\\.[0-9]+(/.*)",
            true,
            null,
            null,
            null
          )),
          yaml(
            """
            spec:
              path: /apps/myapp/v1.2.3/config/settings.yaml
            """,
            """
            spec:
              path: /apps/myapp/v2.0.0/config/settings.yaml
            """
          )
        );
    }

    @Test
    void respectsFilePattern() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "3",
            null,
            null,
            null,
            null,
            "**/k8s/**/*.yaml"
          )),
          yaml(
            """
            spec:
              replicas: 1
            """,
            """
            spec:
              replicas: 3
            """,
            s -> s.path("k8s/deployments/app.yaml")
          ),
          yaml(
            """
            spec:
              replicas: 1
            """,
            s -> s.path("other/app.yaml")
          )
        );
    }

    @Test
    void supportsGlobPatternInPropertyKey() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.*.replicas",
            "5",
            null,
            null,
            null,
            null,
            null
          )),
          yaml(
            """
            spec:
              deployment:
                replicas: 1
              statefulset:
                replicas: 2
            """,
            """
            spec:
              deployment:
                replicas: 5
              statefulset:
                replicas: 5
            """
          )
        );
    }

    @Test
    void loadsFromYamlDefinition() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.ChangeReplicas
            displayName: Change replicas
            description: Change replicas to 10.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  propertyKey: spec.replicas
                  newValue: "10"
            """, "test.ChangeReplicas"),
          yaml(
            """
            spec:
              replicas: 1
            """,
            """
            spec:
              replicas: 10
            """
          )
        );
    }

    @Test
    void loadsFromYamlWithRegex() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.UpdateVersion
            displayName: Update version
            description: Update version in path.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  propertyKey: spec.path
                  newValue: "$1v2.0.0$2"
                  oldValue: "(.*/)v[0-9]+\\\\.[0-9]+\\\\.[0-9]+(/.*)"
                  regex: true
            """, "test.UpdateVersion"),
          yaml(
            """
            spec:
              path: /apps/myapp/v1.2.3/config/settings.yaml
            """,
            """
            spec:
              path: /apps/myapp/v2.0.0/config/settings.yaml
            """
          )
        );
    }

    // Tests for condition feature

    @Test
    void updatesWhenConditionMatches() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            null,
            null,
            null,
            List.of(new ChangeYamlPropertyConditionally.Condition("kind", "Deployment")),
            null
          )),
          yaml(
            """
            kind: Deployment
            spec:
              replicas: 1
            """,
            """
            kind: Deployment
            spec:
              replicas: 5
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenConditionDoesNotMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            null,
            null,
            null,
            List.of(new ChangeYamlPropertyConditionally.Condition("kind", "Deployment")),
            null
          )),
          yaml(
            """
            kind: StatefulSet
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void conditionWorksWithMultiDocumentYaml() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            null,
            null,
            null,
            List.of(new ChangeYamlPropertyConditionally.Condition("kind", "Deployment")),
            null
          )),
          yaml(
            """
            kind: Deployment
            spec:
              replicas: 1
            ---
            kind: StatefulSet
            spec:
              replicas: 1
            """,
            """
            kind: Deployment
            spec:
              replicas: 5
            ---
            kind: StatefulSet
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void conditionWorksWithNestedProperty() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            null,
            null,
            null,
            List.of(new ChangeYamlPropertyConditionally.Condition("metadata.labels.environment", "production")),
            null
          )),
          yaml(
            """
            metadata:
              labels:
                environment: production
            spec:
              replicas: 1
            """,
            """
            metadata:
              labels:
                environment: production
            spec:
              replicas: 5
            """
          )
        );
    }

    @Test
    void multipleConditionsAllMustMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            "spec.replicas",
            "5",
            null,
            null,
            null,
            List.of(
              new ChangeYamlPropertyConditionally.Condition("kind", "Deployment"),
              new ChangeYamlPropertyConditionally.Condition("metadata.labels.environment", "production")
            ),
            null
          )),
          // Both conditions match
          yaml(
            """
            kind: Deployment
            metadata:
              labels:
                environment: production
            spec:
              replicas: 1
            """,
            """
            kind: Deployment
            metadata:
              labels:
                environment: production
            spec:
              replicas: 5
            """
          ),
          // Only one condition matches - no change
          yaml(
            """
            kind: Deployment
            metadata:
              labels:
                environment: staging
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void loadsConditionFromYaml() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.ConditionalChange
            displayName: Conditional change
            description: Change replicas only for Deployments.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  propertyKey: spec.replicas
                  newValue: "10"
                  conditions:
                    - key: kind
                      value: Deployment
            """, "test.ConditionalChange"),
          yaml(
            """
            kind: Deployment
            spec:
              replicas: 1
            """,
            """
            kind: Deployment
            spec:
              replicas: 10
            """
          ),
          yaml(
            """
            kind: StatefulSet
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void loadsMultipleConditionsFromYaml() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.MultiCondition
            displayName: Multiple conditions
            description: Change replicas only for production Deployments.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  propertyKey: spec.replicas
                  newValue: "10"
                  conditions:
                    - key: kind
                      value: Deployment
                    - key: metadata.labels.environment
                      value: production
            """, "test.MultiCondition"),
          yaml(
            """
            kind: Deployment
            metadata:
              labels:
                environment: production
            spec:
              replicas: 1
            """,
            """
            kind: Deployment
            metadata:
              labels:
                environment: production
            spec:
              replicas: 10
            """
          ),
          yaml(
            """
            kind: Deployment
            metadata:
              labels:
                environment: staging
            spec:
              replicas: 1
            """
          )
        );
    }
}
