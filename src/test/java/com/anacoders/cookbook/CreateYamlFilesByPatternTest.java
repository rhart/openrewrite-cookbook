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
package com.anacoders.cookbook;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import static org.openrewrite.yaml.Assertions.yaml;
class CreateYamlFilesByPatternTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CreateYamlFilesByPattern(
                "projects/*/config.yaml",
                "apiVersion: v1\nkind: Config\nmetadata:\n  name: example"
        ));
    }
    @DocumentExample
    @Test
    void createsFilesInMatchingDirectoriesAndLeavesExistingFilesUntouched() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "projects/*/config.yaml",
                        "apiVersion: v1\nkind: Config\nmetadata:\n  name: example"
                )).expectedCyclesThatMakeChanges(2),
                // Existing files to establish directory structure
                yaml(
                        """
                        # Existing file in project-a
                        name: project-a
                        """,
                        spec -> spec.path("projects/project-a/existing.yaml")
                ),
                yaml(
                        """
                        # Existing file in project-b
                        name: project-b
                        """,
                        spec -> spec.path("projects/project-b/existing.yaml")
                ),
                yaml(
                        """
                        # Existing file in project-c
                        name: project-c
                        """,
                        spec -> spec.path("projects/project-c/existing.yaml")
                ),
                // Existing config file should not be modified
                yaml(
                        """
                        existingKey: existingValue
                        """,
                        spec -> spec.path("projects/project-a/config.yaml")
                ),
                // Files that should be created (only in dirs without existing config)
                yaml(
                        doesNotExist(),
                        """
                        apiVersion: v1
                        kind: Config
                        metadata:
                          name: example
                        """,
                        spec -> spec.path("projects/project-b/config.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        apiVersion: v1
                        kind: Config
                        metadata:
                          name: example
                        """,
                        spec -> spec.path("projects/project-c/config.yaml")
                )
        );
    }
    @Test
    void doesNothingWhenNoMatchingDirectories() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "projects/*/config.yaml",
                        "content: value"
                )).expectedCyclesThatMakeChanges(0),
                yaml(
                        """
                        # Some other file
                        other: file
                        """,
                        spec -> spec.path("src/other.yaml")
                )
        );
    }
    @Test
    void worksWithNestedAndDeeplyNestedDirectories() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "src/**/resources/application.yaml",
                        "server:\n  port: 8080"
                )).expectedCyclesThatMakeChanges(2),
                // Shallow nesting
                yaml(
                        """
                        dummy: value
                        """,
                        spec -> spec.path("src/resources/dummy.yaml")
                ),
                // Deep nesting
                yaml(
                        """
                        dummy: value
                        """,
                        spec -> spec.path("src/main/java/resources/dummy.yaml")
                ),
                // Very deep nesting
                yaml(
                        """
                        dummy: value
                        """,
                        spec -> spec.path("src/main/resources/config/resources/dummy.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        server:
                          port: 8080
                        """,
                        spec -> spec.path("src/resources/application.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        server:
                          port: 8080
                        """,
                        spec -> spec.path("src/main/resources/application.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        server:
                          port: 8080
                        """,
                        spec -> spec.path("src/main/java/resources/application.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        server:
                          port: 8080
                        """,
                        spec -> spec.path("src/main/resources/config/resources/application.yaml")
                )
        );
    }
    @Test
    void handlesMultipleWildcardsWithStarAndDoubleStar() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "apps/*/config/**/settings.yaml",
                        "enabled: true"
                )).expectedCyclesThatMakeChanges(2),
                yaml(
                        """
                        dummy: value
                        """,
                        spec -> spec.path("apps/frontend/config/dev/dummy.yaml")
                ),
                yaml(
                        """
                        dummy: value
                        """,
                        spec -> spec.path("apps/backend/config/prod/nested/dummy.yaml")
                ),
                // ** matches zero segments
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        spec -> spec.path("apps/frontend/config/settings.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        spec -> spec.path("apps/backend/config/settings.yaml")
                ),
                // ** matches one segment
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        spec -> spec.path("apps/frontend/config/dev/settings.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        spec -> spec.path("apps/backend/config/prod/settings.yaml")
                ),
                // ** matches multiple segments
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        spec -> spec.path("apps/backend/config/prod/nested/settings.yaml")
                )
        );
    }
    @Test
    void skipsPartiallyMatchingDirectories() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "projects/*/config.yaml",
                        "content: value"
                )),
                // This file is in 'projects' but not in a subdirectory
                yaml(
                        """
                        not: matched
                        """,
                        spec -> spec.path("projects/root.yaml")
                ),
                // This file is in a subdirectory of projects
                yaml(
                        """
                        matched: true
                        """,
                        spec -> spec.path("projects/subdir/existing.yaml")
                ),
                // Only the subdirectory should get the new file
                yaml(
                        doesNotExist(),
                        """
                        content: value
                        """,
                        spec -> spec.path("projects/subdir/config.yaml")
                )
        );
    }
    @Test
    void supportsDoubleStarRecursiveMatching() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "src/**/config.yaml",
                        "k: v"
                )).expectedCyclesThatMakeChanges(2),
                // Establish various directory depths
                yaml(
                        """
                        a: 1
                        """,
                        s -> s.path("src/existing.yaml")
                ),
                yaml(
                        """
                        b: 2
                        """,
                        s -> s.path("src/main/existing.yaml")
                ),
                yaml(
                        """
                        c: 3
                        """,
                        s -> s.path("src/main/java/existing.yaml")
                ),
                // ** should match at all depths under 'src' (including zero segments)
                yaml(
                        doesNotExist(),
                        """
                        k: v
                        """,
                        s -> s.path("src/config.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        k: v
                        """,
                        s -> s.path("src/main/config.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        k: v
                        """,
                        s -> s.path("src/main/java/config.yaml")
                )
        );
    }
    @Test
    void exactPathAtRoot() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "root.yaml",
                        "x: y"
                )),
                yaml(
                        doesNotExist(),
                        """
                        x: y
                        """,
                        s -> s.path("root.yaml")
                )
        );
    }
    @Test
    void multipleDoubleStarsWithZeroSegmentMatching() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "apps/**/config/**/settings.yaml",
                        "enabled: true"
                )).expectedCyclesThatMakeChanges(2),
                yaml(
                        """
                        t: 1
                        """,
                        s -> s.path("apps/frontend/config/dev/other.yaml")
                ),
                yaml(
                        """
                        t: 2
                        """,
                        s -> s.path("apps/backend/config/prod/other.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        s -> s.path("apps/frontend/config/dev/settings.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        s -> s.path("apps/backend/config/prod/settings.yaml")
                ),
                // Additional expected files due to '**' matching zero segments after 'config'
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        s -> s.path("apps/frontend/config/settings.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        enabled: true
                        """,
                        s -> s.path("apps/backend/config/settings.yaml")
                )
        );
    }
    @Test
    void filenameWithDots() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "config/*/application.prod.yaml",
                        "env: production"
                )),
                yaml(
                        """
                        existing: file
                        """,
                        s -> s.path("config/app1/existing.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        env: production
                        """,
                        s -> s.path("config/app1/application.prod.yaml")
                )
        );
    }
    @Test
    void wildcardWithinDirectoryName() {
        rewriteRun(
                spec -> spec.recipe(new CreateYamlFilesByPattern(
                        "projects/project-*/config.yaml",
                        "content: value"
                )).expectedCyclesThatMakeChanges(2),
                yaml(
                        """
                        existing: file
                        """,
                        s -> s.path("projects/project-a/existing.yaml")
                ),
                yaml(
                        """
                        existing: file
                        """,
                        s -> s.path("projects/project-b/existing.yaml")
                ),
                yaml(
                        """
                        existing: file
                        """,
                        s -> s.path("projects/other/existing.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        content: value
                        """,
                        s -> s.path("projects/project-a/config.yaml")
                ),
                yaml(
                        doesNotExist(),
                        """
                        content: value
                        """,
                        s -> s.path("projects/project-b/config.yaml")
                )
        );
    }
}
