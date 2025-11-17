# OpenRewrite Cookbook

[![JitPack](https://jitpack.io/v/rhart/openrewrite-cookbook.svg)](https://jitpack.io/#rhart/openrewrite-cookbook)

Custom OpenRewrite recipes by Anacoders.

## CreateYamlFilesByPattern

Creates YAML files in multiple directories matching a wildcard pattern. Files are only created if they don't already exist.

### Quick Example

Create a `config.yaml` file in every subdirectory under `projects/`:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.CreateProjectConfigs
displayName: Create config files in all projects
recipeList:
  - com.anacoders.cookbook.CreateYamlFilesByPattern:
      filePattern: projects/*/config.yaml
      fileContents: |
        apiVersion: v1
        kind: Config
        metadata:
          name: example
```

### Pattern Examples

| Pattern | Description | Creates Files In |
|---------|-------------|------------------|
| `projects/*/config.yaml` | Single wildcard `*` | Each direct subdirectory of `projects/` |
| `apps/*/deployment.yaml` | Works at any depth | Each subdirectory under `apps/` |
| `src/main/*/application.yaml` | Nested paths | Each subdirectory under `src/main/` |
| `apps/*/config/*/settings.yaml` | Multiple wildcards | Matching nested paths |

**Note:** `**` for recursive matching is mentioned in the recipe description but not fully implemented yet.

### Behavior

- ✅ Creates files only in directories that match the pattern
- ✅ Skips creation if the file already exists (never overwrites)
- ✅ Works with any directory depth and nesting
- ✅ Supports multiple `*` wildcards in a single pattern

## Using This Recipe

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
    rewrite("com.github.rhart:openrewrite-cookbook:v0.1.0")
    
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

You can also use the recipe directly in code:

```java
var recipe = new CreateYamlFilesByPattern(
    "projects/*/config.yaml",
    "apiVersion: v1\nkind: Config"
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
