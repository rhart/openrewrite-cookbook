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

Change a YAML property, optionally only when specified conditions are met. Expects dot notation for nested YAML mappings. Based on `org.openrewrite.yaml.ChangePropertyValue` with added support for conditional execution.

#### Conditional Example

Update `spec.replicas` only in Deployments in the production environment:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.ScaleProductionDeployments
displayName: Scale production deployments
recipeList:
  - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
      propertyKey: spec.replicas
      newValue: "5"
      conditions:
        - key: kind
          value: Deployment
        - key: metadata.labels.environment
          value: production
```

#### Regex Replacement Example

Update the version in a path while preserving prefix and suffix using capture groups, only for Deployments:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.UpdateVersion
displayName: Update version in path
recipeList:
  - com.anacoders.cookbook.yaml.ChangeYamlPropertyConditionally:
      propertyKey: spec.path
      oldValue: "(.*/)v[0-9]+\\.[0-9]+\\.[0-9]+(/.*)"
      newValue: "$1v2.0.0$2"
      regex: true
      conditions:
        - key: kind
          value: Deployment
```

This transforms `/apps/myapp/v1.2.3/config/settings.yaml` into `/apps/myapp/v2.0.0/config/settings.yaml`.

#### Options

| Option | Description | Example |
|--------|-------------|---------|
| `propertyKey` | The key to look for (dot notation). Supports glob patterns. | `spec.replicas` |
| `newValue` | The new value to set | `3` |
| `oldValue` | Only change if current value matches (exact match, or regex if `regex: true`) | `1` |
| `regex` | If `true`, interpret `oldValue` as a regex pattern (default: `false`) | `true` |
| `relaxedBinding` | Use relaxed binding rules for matching (default: `true`) | `false` |
| `conditions` | List of conditions that must ALL match (AND logic) for change to occur | See examples |
| `conditions[].key` | Property key to check (dot notation) | `kind` |
| `conditions[].value` | Value that the property must have | `Deployment` |
| `filePattern` | Optional glob to filter files | `**/k8s/**/*.yaml` |

#### Behavior

- ✅ Uses dot notation for nested properties (e.g., `spec.replicas`)
- ✅ Supports glob patterns in property key (e.g., `spec.*.replicas`)
- ✅ Regex-based replacement with capture groups (`$1`, `$2`, etc.) when `regex: true`
- ✅ Multiple conditions with AND logic (all must match)
- ✅ Conditions evaluated per YAML document (supports multi-document files)
- ✅ No change if target value already matches
- ✅ No change if `oldValue` doesn't match current value
- ✅ Optional file pattern filtering
- ✅ Supports relaxed binding (kebab-case, camelCase matching)

### ChangeHclAttributeConditionally

Change an HCL attribute value based on comment-based conditions. Useful for updating Terraform configurations conditionally.

#### Conditional Example

Update `version` only in modules marked with a `# release-channel: stable` comment:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.UpdateStableModules
displayName: Update stable module versions
recipeList:
  - com.anacoders.cookbook.hcl.ChangeHclAttributeConditionally:
      attributeName: version
      newValue: "2.0.0"
      commentConditions:
        - pattern: "# release-channel:"
          value: stable
```

This transforms:
```hcl
module "app" {
  # release-channel: stable
  source = "registry.example.com/modules/app"
  version = "1.0.0"
}
```

Into:
```hcl
module "app" {
  # release-channel: stable
  source = "registry.example.com/modules/app"
  version = "2.0.0"
}
```

#### Regex Replacement Example

Update the minor version while preserving the patch version:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.BumpMinorVersion
displayName: Bump minor version
recipeList:
  - com.anacoders.cookbook.hcl.ChangeHclAttributeConditionally:
      attributeName: version
      oldValue: "1\\.0\\.(\\d+)"
      newValue: "2.0.$1"
      regex: true
      commentConditions:
        - pattern: "# release-channel:"
          value: stable
```

#### Options

| Option | Description | Example |
|--------|-------------|---------|
| `attributeName` | The HCL attribute name to update | `version` |
| `newValue` | The new value to set | `2.0.0` |
| `oldValue` | Only change if current value matches (exact match, or regex if `regex: true`) | `1.0.0` |
| `regex` | If `true`, interpret `oldValue` as a regex pattern (default: `false`) | `true` |
| `commentConditions` | List of comment conditions that must ALL match (AND logic) | See examples |
| `commentConditions[].pattern` | Comment text pattern to match (include `#` prefix) | `# release-channel:` |
| `commentConditions[].value` | Value that must appear after the pattern | `stable` |
| `filePattern` | Optional glob to filter files | `**/*.tf` |

#### Behavior

- ✅ Updates HCL attribute values in Terraform files
- ✅ Comment-based conditions check comments within the containing block
- ✅ Multiple conditions with AND logic (all must match)
- ✅ Regex-based replacement with capture groups (`$1`, `$2`, etc.) when `regex: true`
- ✅ No change if target value already matches
- ✅ No change if `oldValue` doesn't match current value
- ✅ Optional file pattern filtering
- ✅ Preserves quoting style (quoted strings stay quoted)

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
    rewrite("com.github.rhart:openrewrite-cookbook:v0.5.0")

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
import com.anacoders.cookbook.hcl.ChangeHclAttributeConditionally;
import java.util.List;

var createRecipe = new CreateYamlFilesByPattern(
    "projects/*/config.yaml",
    "apiVersion: v1\nkind: Config"
);

// Simple replacement
var changeRecipe = new ChangeYamlPropertyConditionally(
    "spec.replicas",   // propertyKey
    "3",               // newValue
    null,              // oldValue
    null,              // regex
    null,              // relaxedBinding
    null,              // conditions
    null               // filePattern
);

// Conditional replacement
var conditionalRecipe = new ChangeYamlPropertyConditionally(
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
);

// Regex replacement with capture groups
var regexRecipe = new ChangeYamlPropertyConditionally(
    "spec.path",                              // propertyKey
    "$1v2.0.0$2",                             // newValue
    "(.*/)v[0-9]+\\.[0-9]+\\.[0-9]+(/.*)",   // oldValue (regex)
    true,                                     // regex
    null,                                     // relaxedBinding
    null,                                     // conditions
    "**/k8s/**/*.yaml"                        // filePattern
);

// HCL attribute change with comment conditions
var hclRecipe = new ChangeHclAttributeConditionally(
    "version",                                // attributeName
    "2.0.0",                                  // newValue
    null,                                     // oldValue
    null,                                     // regex
    List.of(
        new ChangeHclAttributeConditionally.CommentCondition(
            "# release-channel:",
            "stable"
        )
    ),
    "**/*.tf"                                 // filePattern
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
