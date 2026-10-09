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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class CopyEmbeddedYamlValueTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CopyEmbeddedYamlValue(
          "$.spec.patches[*].patch",
          "spec.values.ingress.host",
          "spec.postBuild.substitute.ingressHost",
          null
        ));
    }

    @DocumentExample
    @Test
    void copiesTheValueIntoAnExistingMapping() {
        rewriteRun(
          yaml(
            """
            kind: Kustomization
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    - op: add
                      path: /spec/values/monitoring
                      value: true
                  target:
                    kind: HelmRelease
                - patch: |
                    kind: HelmRelease
                    spec:
                      values:
                        replicas: ${replicas}
                        ingress:
                          enabled: true
                          host: app.example.com
                  target:
                    kind: HelmRelease
            """,
            """
            kind: Kustomization
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressHost: "app.example.com"
              patches:
                - patch: |
                    - op: add
                      path: /spec/values/monitoring
                      value: true
                  target:
                    kind: HelmRelease
                - patch: |
                    kind: HelmRelease
                    spec:
                      values:
                        replicas: ${replicas}
                        ingress:
                          enabled: true
                          host: app.example.com
                  target:
                    kind: HelmRelease
            """
          )
        );
    }

    @Test
    void createsMissingParentKeys() {
        rewriteRun(
          yaml(
            """
            kind: Kustomization
            spec:
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """,
            """
            kind: Kustomization
            spec:
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
              postBuild:
                substitute:
                  ingressHost: "app.example.com"
            """
          )
        );
    }

    @Test
    void writesATopLevelProperty() {
        rewriteRun(
          spec -> spec.recipe(new CopyEmbeddedYamlValue(
            "$.spec.patches[*].patch",
            "spec.values.ingress.host",
            "ingressHost",
            null
          )),
          yaml(
            """
            spec:
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """,
            """
            spec:
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            ingressHost: "app.example.com"
            """
          )
        );
    }

    @Test
    void replacesAnExistingValue() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  ingressHost: "old.example.com"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """,
            """
            spec:
              postBuild:
                substitute:
                  ingressHost: "app.example.com"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """
          )
        );
    }

    @Test
    void leavesAMatchingValueAlone() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  ingressHost: "app.example.com"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """
          )
        );
    }

    @Test
    void skipsPatchesWithoutThePropertyAndUsesTheNextOne() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        replicas: 2
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: ignored.example.com
            """,
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressHost: "app.example.com"
              patches:
                - patch: |
                    spec:
                      values:
                        replicas: 2
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: ignored.example.com
            """
          )
        );
    }

    @Test
    void honoursAJsonPathFilterOnTheSource() {
        rewriteRun(
          spec -> spec.recipe(new CopyEmbeddedYamlValue(
            "$.spec.patches[?(@.patch =~ '(?s).*name: app.*')].patch",
            "spec.values.ingress.host",
            "spec.postBuild.substitute.ingressHost",
            null
          )),
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    metadata:
                      name: other
                    spec:
                      values:
                        ingress:
                          host: other.example.com
                - patch: |
                    metadata:
                      name: app
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """,
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressHost: "app.example.com"
              patches:
                - patch: |
                    metadata:
                      name: other
                    spec:
                      values:
                        ingress:
                          host: other.example.com
                - patch: |
                    metadata:
                      name: app
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """
          )
        );
    }

    @Test
    void honoursTheFilePattern() {
        rewriteRun(
          spec -> spec.recipe(new CopyEmbeddedYamlValue(
            "$.spec.patches[*].patch",
            "spec.values.ingress.host",
            "spec.postBuild.substitute.ingressHost",
            "**/*.yaml"
          )),
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: app.example.com
            """,
            spec -> spec.path("app.yml")
          )
        );
    }

    @Test
    void readsEachDocumentSeparately() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: first.example.com
            ---
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: second.example.com
            ---
            spec:
              postBuild:
                substitute:
                  environment: "production"
            """,
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressHost: "first.example.com"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: first.example.com
            ---
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressHost: "second.example.com"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: second.example.com
            ---
            spec:
              postBuild:
                substitute:
                  environment: "production"
            """
          )
        );
    }

    @Test
    void quotesScalarsThatWouldOtherwiseChangeType() {
        rewriteRun(
          spec -> spec.recipe(new CopyEmbeddedYamlValue(
            "$.spec.patches[*].patch",
            "spec.values.ingress.enabled",
            "spec.postBuild.substitute.ingressEnabled",
            null
          )),
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          enabled: true
            """,
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressEnabled: "true"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          enabled: true
            """
          )
        );
    }

    @Test
    void keepsSubstitutionPlaceholdersAndUrlsIntact() {
        rewriteRun(
          spec -> spec.recipe(new CopyEmbeddedYamlValue(
            "$.spec.patches[*].patch",
            "spec.values.ingress.url",
            "spec.postBuild.substitute.ingressUrl",
            null
          )),
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          url: https://app.${domain}:443/path
            """,
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressUrl: "https://app.${domain}:443/path"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          url: https://app.${domain}:443/path
            """
          )
        );
    }

    @Test
    void escapesQuotesInTheCopiedValue() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: 'say "hi"'
            """,
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
                  ingressHost: "say \\"hi\\""
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host: 'say "hi"'
            """
          )
        );
    }

    @Test
    void matchesTheFullPathOnly() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        host: not-under-ingress.example.com
                        ingress:
                          enabled: true
            """
          )
        );
    }

    @Test
    void ignoresAPropertyWhoseValueIsNotAScalar() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        ingress:
                          host:
                            name: app.example.com
            """
          )
        );
    }

    @Test
    void ignoresEmbeddedDocumentsThatAreNotMappings() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    - op: add
                      path: /spec/values/ingress/host
                      value: app.example.com
                - patch: plain text, not a document
            """
          )
        );
    }

    @Test
    void doesNothingWhenNoPatchHoldsTheProperty() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
              patches:
                - patch: |
                    spec:
                      values:
                        replicas: 2
            """
          )
        );
    }

    @Test
    void doesNothingWithoutPatches() {
        rewriteRun(
          yaml(
            """
            spec:
              postBuild:
                substitute:
                  environment: "production"
            """
          )
        );
    }
}
