package com.hrsinternational.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tool for listing Java source files in a directory.
 *
 * <p>This tool discovers {@code .java} files within a given directory and returns
 * their paths as a newline-separated list. It supports both flat (single-level)
 * and recursive directory traversal.
 *
 * <p><strong>Filtering:</strong> Only files with the {@code .java} extension are
 * included in the output. All other file types are silently skipped.
 *
 * <p><strong>Error handling policy:</strong> This tool never throws exceptions.
 * All error conditions are returned as descriptive error strings so the LLM
 * can reason about failures and decide how to proceed.
 */
public final class ListDirectoryTool {

    /**
     * Lists Java source files in the specified directory.
     *
     * @param path      the absolute or relative path to the directory to scan
     * @param recursive if {@code true}, recursively traverse all subdirectories;
     *                  if {@code false}, only list files in the top-level directory
     * @return a newline-separated list of {@code .java} file paths, or an error
     *         message if the directory cannot be listed
     */
    public String execute(String path, boolean recursive) {
        if (path == null || path.isBlank()) {
            return "[ERROR] Path must not be null or blank.";
        }

        Path dirPath;
        try {
            dirPath = Path.of(path).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "[ERROR] Invalid path: " + e.getMessage();
        }

        if (!Files.exists(dirPath)) {
            return "[ERROR] Directory does not exist: " + dirPath;
        }

        if (!Files.isDirectory(dirPath)) {
            return "[ERROR] Path is not a directory: " + dirPath;
        }

        try (Stream<Path> stream = recursive ? Files.walk(dirPath) : Files.list(dirPath)) {
            String result = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));

            if (result.isEmpty()) {
                return "[INFO] No .java files found in: " + dirPath;
            }
            return result;
        } catch (IOException e) {
            return "[ERROR] Failed to list directory '%s': %s".formatted(dirPath, e.getMessage());
        }
    }
}
