# OpenRewrite Cookbook

[![JitPack](https://jitpack.io/v/rhart/openrewrite-cookbook.svg)](https://jitpack.io/#rhart/openrewrite-cookbook)

Custom OpenRewrite recipes by Anacoders.

## Recipes

### CreateYamlFilesByPattern

Creates YAML files in multiple directories matching a wildcard pattern. Files are only created if they don't already exist.

#### Quick Example

Create a `config.yaml` file in every subdirectory under `projects/`:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.CreateProjectConfigs
displayName: Create config files in all projects
recipeList:
  - com.anacoders.cookbook.yaml.CreateYamlFilesByPattern:
      filePattern: projects/*/config.yaml
      fileContents: |
        apiVersion: v1
        kind: Config
        metadata:
          name: example
```

#### Pattern Examples

| Pattern | Description | Creates Files In |
|---------|-------------|------------------|
| `projects/*/config.yaml` | Single wildcard `*` | Each direct subdirectory of `projects/` |
| `apps/*/deployment.yaml` | Works at any depth | Each subdirectory under `apps/` |
| `src/main/*/application.yaml` | Nested paths | Each subdirectory under `src/main/` |
| `apps/*/config/*/settings.yaml` | Multiple wildcards | Matching nested paths |
| `src/**/config.yaml` | Recursive `**` | Any depth under `src/` |

#### Behavior

- ✅ Creates files only in directories that match the pattern
- ✅ Skips creation if the file already exists (never overwrites)
- ✅ Works with any directory depth and nesting
- ✅ Supports multiple `*` wildcards in a single pattern

### ChangeYamlPropertyConditionally

Updates a YAML property value only when another property in the same document matches a specified condition. Useful for updating values in multi-document YAML files where each document should be evaluated independently.

#### Quick Example

Update `replicas` to `3` only in deployments where `environment` is `production`:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.ScaleProductionDeployments
displayName: Scale production deployments
recipeList:
  - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
      conditionJsonPath: $.metadata.labels.environment
      conditionValue: production
      targetJsonPath: $.spec.replicas
      newValue: "3"
```

#### Options

| Option | Description | Example |
|--------|-------------|---------|
| `conditionJsonPath` | JsonPath to the property that must match | `$.metadata.labels.environment` |
| `conditionValue` | The value that conditionJsonPath must equal | `production` |
| `targetJsonPath` | JsonPath to the property to update | `$.spec.replicas` |
| `newValue` | The new value to set | `3` |
| `filePattern` | Optional glob to filter files | `**/k8s/**/*.yaml` |

#### Behavior

- ✅ Updates target property only when condition matches
- ✅ Handles multi-document YAML files (each document evaluated independently)
- ✅ No change if target value already matches
- ✅ No change if condition or target property is missing
- ✅ Optional file pattern filtering

## Using These Recipes

### In a Gradle Project

Add to your `build.gradle.kts` or `build.gradle`:

```kotlin
plugins {
    id("org.openrewrite.rewrite") version("latest.release")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Use a specific release version (recommended)
    rewrite("com.github.rhart:openrewrite-cookbook:v0.2.0")
    
    // Or use the latest commit from main branch (snapshot)
    // rewrite("com.github.rhart:openrewrite-cookbook:main-SNAPSHOT")
    
    // Or use a specific commit hash
    // rewrite("com.github.rhart:openrewrite-cookbook:5811e78")
}
```

Then run:
```bash
./gradlew rewriteRun
```

### Direct Usage

You can also use the recipes directly in code:

```java
import com.anacoders.cookbook.yaml.CreateYamlFilesByPattern;
import com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally;

var createRecipe = new CreateYamlFilesByPattern(
    "projects/*/config.yaml",
    "apiVersion: v1\nkind: Config"
);

var changeRecipe = new ChangeYamlPropertyConditionally(
    "$.metadata.labels.environment",
    "production",
    "$.spec.replicas",
    "3",
    null
);
```

## Development

Build and test:
```bash
./gradlew build test
```

Test locally in another project:
```bash
./gradlew publishToMavenLocal
```

Then in your test project, use `mavenLocal()` in repositories and reference:
```kotlin
rewrite("com.anacoders:openrewrite-cookbook:latest.integration")
```

## License

Apache License 2.0

## Publishing a Release

This project uses JitPack for distribution - no manual publishing needed!

### Create a Release

1. **Tag a version:**
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

2. **Create a GitHub Release** (optional but recommended):
   - Go to your GitHub repository
   - Click "Releases" → "Create a new release"
   - Select your tag (e.g., `v0.1.0`)
   - Add release notes describing changes
   - Publish the release

3. **JitPack builds automatically!**
   - Visit https://jitpack.io/#rhart/openrewrite-cookbook
   - Your release will be available within a few minutes
   - Users can then add it as a dependency

### Using Latest Commit (Development Builds)

For testing unreleased features, users can depend on a specific commit:

1. **Get the commit hash** from GitHub (short form, first 7-10 characters)
2. **Use it as the version:**
   ```kotlin
   rewrite("com.github.rhart:openrewrite-cookbook:abc1234")
   ```

**Note:** JitPack will build the commit on first request, which may take a few minutes. Check the build status at https://jitpack.io/#rhart/openrewrite-cookbook
