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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.*;
import org.openrewrite.yaml.YamlParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

@NullMarked
@Value
@EqualsAndHashCode(callSuper = false)
public class CreateYamlFilesByPattern extends ScanningRecipe<CreateYamlFilesByPattern.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Create YAML files by pattern";
    }

    @Override
    public String getDescription() {
        return "Creates YAML files in directories matching a glob-like pattern. Supports * (single segment or within segment) and ** (zero or more segments). " +
                "Files are only created if they don't already exist. Example: 'projects/*/config.yaml' creates config.yaml in each direct child of projects/. " +
                "Example: 'projects/project-*/config.yaml' matches directories like 'project-a', 'project-b'. " +
                "Example: 'src/**/config/app.yaml' matches any 'config' directory under src at any depth.";
    }

    @Option(displayName = "File pattern",
            description = "Pattern with wildcards for where to create files. Use * for single directory wildcard or within segment (e.g., 'project-*'), ** for recursive. " +
                    "Example: 'projects/*/config.yaml' creates config.yaml in each direct child of projects/",
            example = "projects/*/config.yaml")
    String filePattern;

    @Option(displayName = "File contents",
            description = "The YAML content to write to each created file.",
            example = "apiVersion: v1\nkind: Config\nmetadata:\n  name: example")
    String fileContents;

    public static class Accumulator {
        Set<Path> existingDirectories = new HashSet<>();
        Set<Path> existingFiles = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        validateOptions();
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    SourceFile sourceFile = (SourceFile) tree;
                    Path sourcePath = sourceFile.getSourcePath();

                    acc.existingFiles.add(sourcePath);

                    // Track all ancestor directories
                    Path parent = sourcePath.getParent();
                    while (parent != null) {
                        acc.existingDirectories.add(parent);
                        parent = parent.getParent();
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Set<Path> targetPaths = resolveTargetFilePaths(acc.existingDirectories);
        if (targetPaths.isEmpty()) {
            return emptyList();
        }

        YamlParser yamlParser = YamlParser.builder().build();
        List<SourceFile> parsed = yamlParser.parse(fileContents).collect(toList());
        if (parsed.isEmpty()) {
            return emptyList();
        }

        SourceFile template = parsed.get(0);
        List<SourceFile> newFiles = new ArrayList<>();
        for (Path target : targetPaths) {
            if (!acc.existingFiles.contains(target)) {
                newFiles.add(template.withSourcePath(target));
            }
        }
        return newFiles;
    }

    private void validateOptions() {
        if (filePattern == null || filePattern.trim().isEmpty()) {
            throw new IllegalArgumentException("filePattern must be provided and not blank");
        }
        if (fileContents == null) {
            throw new IllegalArgumentException("fileContents must not be null");
        }
        if (filePattern.endsWith("/")) {
            throw new IllegalArgumentException("filePattern must not end with a slash; specify a file name at the end");
        }
    }

    private List<String> splitPattern(String pattern) {
        return Arrays.stream(pattern.split("/"))
                .filter(seg -> !seg.isEmpty())
                .collect(toList());
    }

    private List<String> pathToSegments(Path path) {
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < path.getNameCount(); i++) {
            segments.add(path.getName(i).toString());
        }
        return segments;
    }

    private boolean matchesPattern(Path path, List<String> patternSegments) {
        return matchSegments(pathToSegments(path), patternSegments, 0, 0);
    }

    private boolean matchSegments(List<String> pathSegs, List<String> patternSegs, int pathIdx, int patternIdx) {
        while (patternIdx < patternSegs.size() && pathIdx < pathSegs.size()) {
            String pattern = patternSegs.get( patternIdx );

            if ("**".equals( pattern )) {
                // Skip consecutive ** wildcards
                while (patternIdx + 1 < patternSegs.size() && "**".equals( patternSegs.get( patternIdx + 1 ) )) {
                    patternIdx++;
                }

                // Trailing ** matches everything remaining
                if (patternIdx + 1 == patternSegs.size()) {
                    return true;
                }

                // Try matching remaining pattern at each position
                for (int i = pathIdx; i <= pathSegs.size(); i++) {
                    if (matchSegments( pathSegs, patternSegs, i, patternIdx + 1 )) {
                        return true;
                    }
                }
                return false;

            }
            if ("*".equals( pattern )) {
                // Single wildcard matches exactly one segment
                pathIdx++;
                patternIdx++;

            } else if (pattern.contains("*") || pattern.contains("?")) {
                // Glob pattern within segment (e.g., "project-*")
                if (!matchGlobPattern(pathSegs.get(pathIdx), pattern)) {
                    return false;
                }
                pathIdx++;
                patternIdx++;

            } else {
                // Literal segment must match exactly
                if (!pattern.equals( pathSegs.get( pathIdx ) )) {
                    return false;
                }
                pathIdx++;
                patternIdx++;
            }
        }

        // Consume trailing ** wildcards
        while (patternIdx < patternSegs.size() && "**".equals(patternSegs.get(patternIdx))) {
            patternIdx++;
        }

        return patternIdx == patternSegs.size() && pathIdx == pathSegs.size();
    }

    /**
     * Matches a path segment against a glob pattern that may contain wildcards.
     * Supports * for matching any sequence of characters within a segment.
     * Supports ? for matching any single character.
     * Examples: "project-*" matches "project-a", "project-foo", etc.
     */
    private boolean matchGlobPattern(String segment, String pattern) {
        // Convert glob pattern to regex
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append(".");
            } else if ("[]{}().+^$|\\".indexOf(c) != -1) {
                // Escape regex special characters
                regex.append("\\").append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        return segment.matches(regex.toString());
    }

    private Set<Path> resolveTargetFilePaths(Set<Path> existingDirectories) {
        List<String> patternSegs = splitPattern(filePattern);
        if (patternSegs.isEmpty()) {
            return emptySet();
        }

        String fileName = patternSegs.get(patternSegs.size() - 1);
        if ("*".equals(fileName) || "**".equals(fileName)) {
            throw new IllegalArgumentException("File name segment must be a concrete name, wildcards not supported");
        }

        List<String> dirPattern = patternSegs.subList(0, patternSegs.size() - 1);

        // Root-level file (no directory pattern)
        if (dirPattern.isEmpty()) {
            return singleton(Paths.get(fileName));
        }

        // Find matching directories
        Set<Path> targets = new HashSet<>();
        for (Path dir : existingDirectories) {
            if (matchesPattern(dir, dirPattern)) {
                targets.add(dir.resolve(fileName));
            }
        }
        return targets;
    }
}
