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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.hcl.Assertions.hcl;

class ChangeHclAttributeConditionallyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeHclAttributeConditionally(
          "version",
          "2.0.0",
          null,
          null,
          List.of(
            new ChangeHclAttributeConditionally.CommentCondition(
              "# release-channel:",
              "stable"
            )
          ),
          null
        ));
    }

    @DocumentExample
    @Test
    void updateAttributeWithMatchingComment() {
        rewriteRun(
          hcl(
            """
            module "app" {
              # release-channel: stable
              source = "registry.example.com/modules/app"

              name = "my-app"
              version = "1.0.0"
              enabled = true
            }
            """,
            """
            module "app" {
              # release-channel: stable
              source = "registry.example.com/modules/app"

              name = "my-app"
              version = "2.0.0"
              enabled = true
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenCommentDoesNotMatch() {
        rewriteRun(
          hcl(
            """
            module "app" {
              # release-channel: beta
              source = "registry.example.com/modules/app"

              version = "1.0.0"
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenAttributeNotPresent() {
        rewriteRun(
          hcl(
            """
            module "app" {
              # release-channel: stable
              source = "registry.example.com/modules/app"

              name = "my-app"
              enabled = true
            }
            """
          )
        );
    }

    @Test
    void updateOnlyMatchingModules() {
        rewriteRun(
          hcl(
            """
            module "app1" {
              # release-channel: stable
              version = "1.0.0"
            }

            module "app2" {
              # release-channel: beta
              version = "1.0.0"
            }

            module "app3" {
              # release-channel: stable
              version = "1.0.0"
            }
            """,
            """
            module "app1" {
              # release-channel: stable
              version = "2.0.0"
            }

            module "app2" {
              # release-channel: beta
              version = "1.0.0"
            }

            module "app3" {
              # release-channel: stable
              version = "2.0.0"
            }
            """
          )
        );
    }

    @Test
    void updateWithRegexOldValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.$1",
            "1\\.0\\.(\\d+)",
            true,
            List.of(
              new ChangeHclAttributeConditionally.CommentCondition(
                "# release-channel:",
                "stable"
              )
            ),
            "**/*.tf"
          )),
          hcl(
            """
            module "app" {
              # release-channel: stable
              version = "1.0.5"
            }
            """,
            """
            module "app" {
              # release-channel: stable
              version = "2.0.5"
            }
            """
          )
        );
    }

    @Test
    void updateWithoutCommentConditions() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            null,
            null,
            null,
            null
          )),
          hcl(
            """
            module "app" {
              version = "1.0.0"
            }
            """,
            """
            module "app" {
              version = "2.0.0"
            }
            """
          )
        );
    }

    @Test
    void updateWithMultipleCommentConditions() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            null,
            null,
            List.of(
              new ChangeHclAttributeConditionally.CommentCondition("# release-channel:", "stable"),
              new ChangeHclAttributeConditionally.CommentCondition("# env:", "prod")
            ),
            null
          )),
          // Both conditions match - should update
          hcl(
            """
            module "app" {
              # release-channel: stable
              # env: prod
              version = "1.0.0"
            }
            """,
            """
            module "app" {
              # release-channel: stable
              # env: prod
              version = "2.0.0"
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenNotAllCommentConditionsMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            null,
            null,
            List.of(
              new ChangeHclAttributeConditionally.CommentCondition("# release-channel:", "stable"),
              new ChangeHclAttributeConditionally.CommentCondition("# env:", "prod")
            ),
            null
          )),
          // Only one condition matches - should NOT update
          hcl(
            """
            module "app" {
              # release-channel: stable
              # env: dev
              version = "1.0.0"
            }
            """
          )
        );
    }

    @Test
    void updateWithOldValueExactMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            "1.0.0",
            false,
            null,
            null
          )),
          hcl(
            """
            module "app" {
              version = "1.0.0"
            }
            """,
            """
            module "app" {
              version = "2.0.0"
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenOldValueDoesNotMatch() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            "1.0.0",
            false,
            null,
            null
          )),
          hcl(
            """
            module "app" {
              version = "1.5.0"
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenValueAlreadyMatches() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            null,
            null,
            null,
            null
          )),
          hcl(
            """
            module "app" {
              version = "2.0.0"
            }
            """
          )
        );
    }

    @Test
    void filePatternRestrictsProcessing() {
        rewriteRun(
          spec -> spec.recipe(new ChangeHclAttributeConditionally(
            "version",
            "2.0.0",
            null,
            null,
            null,
            "**/modules/*.tf"
          )),
          // File matching pattern - should be updated
          hcl(
            """
            module "app" {
              version = "1.0.0"
            }
            """,
            """
            module "app" {
              version = "2.0.0"
            }
            """,
            spec -> spec.path("modules/main.tf")
          ),
          // File NOT matching pattern - should NOT be updated
          hcl(
            """
            module "app" {
              version = "1.0.0"
            }
            """,
            spec -> spec.path("other/main.tf")
          )
        );
    }
}
