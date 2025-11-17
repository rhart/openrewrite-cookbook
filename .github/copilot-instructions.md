# GitHub Copilot Instructions for openrewrite-cookbook

## Project Overview
This is a custom OpenRewrite recipe repository for creating and testing OpenRewrite recipes. It was generated from the [moderneinc/rewrite-recipe-starter](https://github.com/moderneinc/rewrite-recipe-starter) template.

**All Maven files and example recipes have been removed.** This project contains only production-ready custom recipes.

## Key Information

### Build System
- **Gradle Only**: This project uses Gradle exclusively. All Maven files have been removed.
- **Group**: `com.anacoders`
- **Project Name**: `openrewrite-cookbook`
- **Java Version**: 25 (Temurin distribution)

### Project Structure
- **Source Code**: `src/main/java/com/anacoders/cookbook/` - Contains OpenRewrite recipes
- **Tests**: `src/test/java/com/anacoders/cookbook/` - Contains tests for all recipes
- **Declarative Recipes**: `src/main/resources/META-INF/rewrite/` - YAML-based recipes
- **Package**: `com.anacoders.cookbook`

### Current Recipes

#### CreateYamlFilesByPattern
Creates YAML files in multiple directories based on wildcard patterns.

**Key Features:**
- Uses `ScanningRecipe` pattern with an `Accumulator` to track existing directories and files
- Only creates files that don't already exist (never overwrites)
- Supports `*` wildcard for single directory matching
- Supports multiple wildcards in a pattern (e.g., `apps/*/config/*/settings.yaml`)
- `**` mentioned in docs but not fully implemented

**Implementation Notes:**
- `getScanner()` - Scans source tree to track existing directories and files matching the pattern
- `generate()` - Creates new YAML files in matching directories where they don't exist
- `getVisitor()` - Returns `TreeVisitor.noop()` since files are never modified, only created

**Test Coverage (9 tests):**
- Creates files in multiple matching directories
- Doesn't modify existing files
- Works with nested directories
- Handles no matching directories gracefully
- Supports single and multiple wildcards
- Only matches complete patterns (not partial)

### Build Commands
```bash
./gradlew build test          # Build and test
./gradlew publishToMavenLocal # Publish to local Maven repo for testing
./gradlew snapshot publish    # Build and publish snapshot
./gradlew final publish       # Build and publish release
```

### Testing
```bash
./gradlew test                                    # Run all tests
./gradlew test --tests CreateYamlFilesByPatternTest  # Run specific test
```

Test results are in: `build/test-results/test/TEST-*.xml`

### GitHub Actions Workflows

1. **ci.yml** - Continuous Integration
   - Runs on push to main and on PRs
   - Builds and tests the project using Gradle
   - Only uses Gradle (no Maven)
   
2. **receive-pr.yml** + **comment-pr.yml** - Automated Code Review
   - When a PR is opened, automatically runs OpenRewrite best practices against the PR code
   - Posts suggested improvements as inline PR comments
   - Uses the recipe: `com.yourorg.ApplyRecipeBestPractices`

### Important Notes
- This is a **Gradle-only** project - do not suggest Maven commands or create pom.xml files
- All recipes should follow OpenRewrite best practices
- Run `./gradlew --init-script init.gradle rewriteRun -Drewrite.activeRecipe=org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices` to apply best practices to your own recipes
- All recipes use the package `com.anacoders.cookbook`
- Tests use `doesNotExist()` instead of `null` for file creation expectations (OpenRewrite best practice)
- Recipe options use `@Option` annotations with clear descriptions and examples

### Code Style
- Use Lombok `@Value` for immutable recipe classes
- Use `@EqualsAndHashCode(callSuper = false)` for ScanningRecipes
- All fields should be `final` (enforced by `@Value`)
- Private helper methods for pattern matching and path resolution
- Clear, descriptive method and variable names

### Dependencies
Uses OpenRewrite recipe BOM for version management:
- `org.openrewrite:rewrite-java`
- `org.openrewrite.recipe:rewrite-java-dependencies`
- `org.openrewrite:rewrite-yaml`
- `org.openrewrite:rewrite-xml`

Parser dependencies:
- `org.jspecify:jspecify:1.0.0` (in parserClasspath)

### Common Patterns
When creating new recipes:
1. Extend `Recipe` for simple transformations or `ScanningRecipe<T>` for recipes that need to scan first then generate
2. Use `@Option` to define configurable parameters
3. Implement required methods: `getDisplayName()`, `getDescription()`
4. For ScanningRecipe: implement `getInitialValue()`, `getScanner()`, `generate()`, and optionally `getVisitor()`
5. Write comprehensive tests using OpenRewrite's test framework
6. Use `doesNotExist()` for files that should be created
7. Test edge cases: no matches, single match, multiple matches, existing files

### Resources
- [OpenRewrite Documentation](https://docs.openrewrite.org/)
- [Recipe Development Guide](https://docs.openrewrite.org/authoring-recipes/recipe-development-environment)
- [Moderne Platform](https://app.moderne.io)
- [OpenRewrite Recipe Testing](https://docs.openrewrite.org/authoring-recipes/recipe-testing)
