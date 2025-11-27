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

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeYamlPropertyConditionallyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeYamlPropertyConditionally(
          "$.metadata.labels.environment",
          "production",
          "$.spec.replicas",
          "3",
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
            "$.metadata.labels.environment",
            "production",
            "$.spec.replicas",
            "3",
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
}
