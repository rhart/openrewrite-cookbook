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

Updates a YAML property value only when all specified conditions in the same document match. Supports multiple conditions (AND logic) and regex-based replacement with capture groups. Useful for updating values in multi-document YAML files where each document should be evaluated independently.

#### Quick Example

Update `replicas` to `3` only in Deployments where `environment` is `production`:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.ScaleProductionDeployments
displayName: Scale production deployments
recipeList:
  - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
      conditions:
        - jsonPath: $.kind
          value: Deployment
        - jsonPath: $.metadata.labels.environment
          value: production
      targetJsonPath: $.spec.replicas
      newValue: "3"
```

#### Regex Replacement Example

Update the version tag while preserving the registry URL using capture groups:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.UpdateImageVersion
displayName: Update container image version
recipeList:
  - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
      conditions:
        - jsonPath: $.kind
          value: Kustomization
      targetJsonPath: $.spec.ref.tag
      oldValue: "(oci://[^:]+):([0-9.]+)"
      newValue: "$1:2025.4.0"
      regex: true
```

This transforms `oci://registry.example.com/app:1.0.0` into `oci://registry.example.com/app:2025.4.0`.

#### Options

| Option | Description | Example |
|--------|-------------|---------|
| `conditions` | List of conditions that must ALL match (AND logic) | See examples above |
| `conditions[].jsonPath` | JsonPath to the property to check | `$.kind` |
| `conditions[].value` | The value that the property must equal | `Deployment` |
| `targetJsonPath` | JsonPath to the property to update | `$.spec.replicas` |
| `oldValue` | Only change if current value matches (exact match, or regex if `regex: true`) | `(oci://[^:]+):([0-9.]+)` |
| `newValue` | The new value (use `$1`, `$2` for capture groups when `regex: true`) | `$1:2025.4.0` |
| `regex` | If `true`, interpret `oldValue` as a regex pattern (default: `false`) | `true` |
| `filePattern` | Optional glob to filter files | `**/k8s/**/*.yaml` |

#### Behavior

- ✅ Updates target property only when ALL conditions match (AND logic)
- ✅ Supports multiple conditions per recipe
- ✅ Regex-based replacement with capture groups (`$1`, `$2`, etc.) when `regex: true`
- ✅ Handles multi-document YAML files (each document evaluated independently)
- ✅ No change if target value already matches
- ✅ No change if condition or target property is missing
- ✅ No change if `oldValue` doesn't match current value
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
    rewrite("com.github.rhart:openrewrite-cookbook:v0.3.0")
    
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
import java.util.List;

var createRecipe = new CreateYamlFilesByPattern(
    "projects/*/config.yaml",
    "apiVersion: v1\nkind: Config"
);

// Simple replacement with conditions
var changeRecipe = new ChangeYamlPropertyConditionally(
    List.of(
        new ChangeYamlPropertyConditionally.Condition("$.kind", "Deployment"),
        new ChangeYamlPropertyConditionally.Condition("$.metadata.labels.environment", "production")
    ),
    "$.spec.replicas",
    null,   // oldValue - no value matching
    "3",    // newValue
    null,   // regex
    null    // filePattern
);

// Regex replacement with capture groups
var regexRecipe = new ChangeYamlPropertyConditionally(
    List.of(new ChangeYamlPropertyConditionally.Condition("$.kind", "Kustomization")),
    "$.spec.ref.tag",
    "(oci://[^:]+):([0-9.]+)",  // oldValue - regex pattern
    "$1:2025.4.0",              // newValue - preserve URL, update version
    true,                       // regex - enable regex mode
    "**/k8s/**/*.yaml"          // filePattern
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
