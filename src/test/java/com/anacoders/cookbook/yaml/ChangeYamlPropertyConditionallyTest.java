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
          List.of(new ChangeYamlPropertyConditionally.Condition(
            "$.metadata.labels.environment",
            "production"
          )),
          "$.spec.replicas",
          null,
          "3",
          null,
          null
        ));
    }

    @DocumentExample
    @Test
    void updatesValueWhenConditionMatches() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 1
              template:
                spec:
                  containers:
                    - name: app
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 3
              template:
                spec:
                  containers:
                    - name: app
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenConditionDoesNotMatch() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: staging
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void handlesMultiDocumentYaml() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: app1
              labels:
                environment: production
            spec:
              replicas: 1
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: app2
              labels:
                environment: staging
            spec:
              replicas: 1
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: app1
              labels:
                environment: production
            spec:
              replicas: 3
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: app2
              labels:
                environment: staging
            spec:
              replicas: 1
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
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 3
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenConditionPropertyMissing() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenTargetPropertyMissing() {
        rewriteRun(
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              template:
                spec:
                  containers:
                    - name: app
            """
          )
        );
    }

    @Test
    void respectsFilePattern() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(new ChangeYamlPropertyConditionally.Condition(
              "$.metadata.labels.environment",
              "production"
            )),
            "$.spec.replicas",
            null,
            "3",
            null,
            "**/k8s/**/*.yaml"
          )),
          // This file matches the pattern - should be updated
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 1
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 3
            """,
            s -> s.path("k8s/deployments/app.yaml")
          ),
          // This file does NOT match the pattern - should NOT be updated
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 1
            """,
            s -> s.path("other/app.yaml")
          )
        );
    }

    // Tests for multiple conditions (AND logic)

    @Test
    void updatesWhenAllConditionsMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "Deployment"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.labels.environment", "production")
            ),
            "$.spec.replicas",
            null,
            "5",
            null,
            null
          )),
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 1
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: production
            spec:
              replicas: 5
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenOnlyOneConditionMatches() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "Deployment"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.labels.environment", "production")
            ),
            "$.spec.replicas",
            null,
            "5",
            null,
            null
          )),
          // kind matches but environment doesn't
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: staging
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void doesNotUpdateWhenNoConditionsMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "StatefulSet"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.labels.environment", "production")
            ),
            "$.spec.replicas",
            null,
            "5",
            null,
            null
          )),
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: my-app
              labels:
                environment: staging
            spec:
              replicas: 1
            """
          )
        );
    }

    @Test
    void updatesWithEmptyConditionsList() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(),
            "$.spec.replicas",
            null,
            "10",
            null,
            null
          )),
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
              replicas: 10
            """
          )
        );
    }

    // Tests for regex-based replacement with capture groups

    @Test
    void replacesUsingCaptureGroups() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(new ChangeYamlPropertyConditionally.Condition("$.kind", "Kustomization")),
            "$.spec.ref.tag",
            "(oci://[^:]+):([0-9.]+)",
            "$1:2025.4.0",
            true,
            null
          )),
          yaml(
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            spec:
              ref:
                tag: "oci://registry.example.com/app:1.0.0"
            """,
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            spec:
              ref:
                tag: "oci://registry.example.com/app:2025.4.0"
            """
          )
        );
    }

    @Test
    void doesNotReplaceWhenPatternDoesNotMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(new ChangeYamlPropertyConditionally.Condition("$.kind", "Kustomization")),
            "$.spec.ref.tag",
            "(oci://[^:]+):([0-9.]+)",
            "$1:2025.4.0",
            true,
            null
          )),
          // Value doesn't match the pattern (no oci:// prefix)
          yaml(
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            spec:
              ref:
                tag: "v1.0.0"
            """
          )
        );
    }

    @Test
    void replacesWithMultipleCaptureGroups() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(),
            "$.spec.image",
            "([^/]+)/([^:]+):(.+)",
            "$1/$2:latest",
            true,
            null
          )),
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            spec:
              image: "docker.io/nginx:1.21.0"
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            spec:
              image: "docker.io/nginx:latest"
            """
          )
        );
    }

    @Test
    void handlesPatternWithNoChangeNeeded() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(),
            "$.spec.image",
            "([^:]+):(.+)",
            "$1:latest",
            true,
            null
          )),
          // Already has the target value
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            spec:
              image: "nginx:latest"
            """
          )
        );
    }

    @Test
    void multipleConditionsWithRegexReplacement() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "Kustomization"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.labels.channel", "stable")
            ),
            "$.spec.ref.tag",
            "(.+):([0-9]+\\.[0-9]+\\.[0-9]+)",
            "$1:2025.4.0",
            true,
            null
          )),
          yaml(
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            metadata:
              labels:
                channel: stable
            spec:
              ref:
                tag: "oci://registry.example.com/app:1.2.3"
            """,
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            metadata:
              labels:
                channel: stable
            spec:
              ref:
                tag: "oci://registry.example.com/app:2025.4.0"
            """
          )
        );
    }

    @Test
    void multiDocumentWithDifferentConditionMatches() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "Kustomization"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.labels.channel", "stable")
            ),
            "$.spec.ref.tag",
            null,
            "v2.0.0",
            null,
            null
          )),
          yaml(
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            metadata:
              labels:
                channel: stable
            spec:
              ref:
                tag: "v1.0.0"
            ---
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            metadata:
              labels:
                channel: beta
            spec:
              ref:
                tag: "v1.0.0"
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              labels:
                channel: stable
            spec:
              ref:
                tag: "v1.0.0"
            """,
            """
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            metadata:
              labels:
                channel: stable
            spec:
              ref:
                tag: "v2.0.0"
            ---
            apiVersion: kustomize.toolkit.fluxcd.io/v1
            kind: Kustomization
            metadata:
              labels:
                channel: beta
            spec:
              ref:
                tag: "v1.0.0"
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              labels:
                channel: stable
            spec:
              ref:
                tag: "v1.0.0"
            """
          )
        );
    }

    @Test
    void updatesWhenConditionMatchesDirectChild() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "Deployment"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.name", "hello-kubernetes")
            ),
            "$.spec.replicas",
            "3",
            "30",
            null,
            null
          )),
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: hello-kubernetes
            spec:
              replicas: 3
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: hello-kubernetes
            spec:
              replicas: 30
            """
          )
        );
    }

    @Test
    void multipleConditionsWithOldValueMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeYamlPropertyConditionally(
            List.of(
              new ChangeYamlPropertyConditionally.Condition("$.kind", "Deployment"),
              new ChangeYamlPropertyConditionally.Condition("$.metadata.name", "hello-kubernetes")
            ),
            "$.spec.selector.matchLabels.foo",
            "firstPart-1.2-secondPart",
            "firstPart-5.4-secondPart",
            null,
            null
          )),
          yaml(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: hello-kubernetes
            spec:
              selector:
                matchLabels:
                  foo: firstPart-1.2-secondPart
            """,
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: hello-kubernetes
            spec:
              selector:
                matchLabels:
                  foo: firstPart-5.4-secondPart
            """
          )
        );
    }

    @Test
    void loadsRecipeFromYamlDefinition() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.SimpleCondition
            displayName: Test simple condition
            description: Test recipe with simple condition.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  conditions:
                    - jsonPath: $.kind
                      value: Deployment
                  targetJsonPath: $.spec.replicas
                  newValue: "5"
            """, "test.SimpleCondition"),
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
    void loadsFromYamlWithMultipleConditions() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.MultiCondition
            displayName: Test multiple conditions
            description: Test recipe with multiple conditions.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  conditions:
                    - jsonPath: $.kind
                      value: Deployment
                    - jsonPath: $.metadata.name
                      value: my-app
                  targetJsonPath: $.spec.replicas
                  newValue: "10"
            """, "test.MultiCondition"),
          yaml(
            """
            kind: Deployment
            metadata:
              name: my-app
            spec:
              replicas: 1
            """,
            """
            kind: Deployment
            metadata:
              name: my-app
            spec:
              replicas: 10
            """
          )
        );
    }

    @Test
    void conditionsAndRegexWorkWhenDefinedInYaml() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: test.RegexCondition
            displayName: Test regex with conditions
            description: Test recipe with regex replacement.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  conditions:
                    - jsonPath: $.kind
                      value: Deployment
                  targetJsonPath: $.spec.path
                  oldValue: "(.*/)v[0-9]+\\\\.[0-9]+\\\\.[0-9]+(/.*)"
                  newValue: "$1v2.0.0$2"
                  regex: true
            """, "test.RegexCondition"),
          yaml(
            """
            kind: Deployment
            spec:
              path: "/apps/myapp/v1.2.3/config/settings.yaml"
            """,
            """
            kind: Deployment
            spec:
              path: "/apps/myapp/v2.0.0/config/settings.yaml"
            """
          )
        );
    }

    @Test
    void replacesVersionInPath() {
        rewriteRun(
          spec -> spec.recipeFromYaml(
            """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: com.example.FixKubernetesManifests
            displayName: Fix Kubernetes manifest selectors
            description: Updates Service selectors to match Deployment labels.
            recipeList:
              - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
                  conditions:
                    - jsonPath: $.kind
                      value: Deployment
                    - jsonPath: $.metadata.name
                      value: hello-kubernetes
                  targetJsonPath: $.spec.selector.matchLabels.foo
                  oldValue: "(.*-)[0-9]+\\\\.[0-9]+(-.*)"
                  newValue: "$15.4$2"
                  regex: true
            """, "com.example.FixKubernetesManifests"),
          yaml(
            """
            apiVersion: v1
            kind: Service
            metadata:
              name: hello-kubernetes
            spec:
              type: LoadBalancer
              ports:
                - port: 80
                  targetPort: 8080
              selector:
                app: goodbye-kubernetes
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: hello-kubernetes
            spec:
              replicas: 3
              selector:
                matchLabels:
                  app: hello-kubernetes
                  foo: firstPart-1.2-secondPart
            """,
            """
            apiVersion: v1
            kind: Service
            metadata:
              name: hello-kubernetes
            spec:
              type: LoadBalancer
              ports:
                - port: 80
                  targetPort: 8080
              selector:
                app: goodbye-kubernetes
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: hello-kubernetes
            spec:
              replicas: 3
              selector:
                matchLabels:
                  app: hello-kubernetes
                  foo: firstPart-5.4-secondPart
            """
          )
        );
    }
}
