package com.hrsinternational.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tool for reading the contents of a source file from disk.
 *
 * <p>This tool is designed to be invoked by an LLM agent via the tool-calling
 * protocol. It performs the following validations before reading:
 * <ul>
 *   <li>The path must exist on disk.</li>
 *   <li>The path must point to a regular file (not a directory or symlink target that is a directory).</li>
 *   <li>The file must be readable by the current process.</li>
 *   <li>The file must not exceed {@value #MAX_FILE_SIZE_BYTES} bytes (100 KB) to prevent
 *       sending excessively large content to the LLM context window.</li>
 * </ul>
 *
 * <p><strong>Error handling policy:</strong> This tool never throws exceptions.
 * All error conditions are returned as descriptive error strings so the LLM
 * can reason about failures and decide how to proceed.
 */
public final class ReadFileTool {

    /** Maximum file size allowed (100 KB). */
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024;

    /**
     * Reads the contents of the file at the given path.
     *
     * @param path the absolute or relative path to the file to read
     * @return the full text content of the file, or an error message describing
     *         why the file could not be read
     */
    public String execute(String path) {
        if (path == null || path.isBlank()) {
            return "[ERROR] Path must not be null or blank.";
        }

        Path filePath;
        try {
            filePath = Path.of(path).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "[ERROR] Invalid path: " + e.getMessage();
        }

        if (!Files.exists(filePath)) {
            return "[ERROR] File does not exist: " + filePath;
        }

        if (!Files.isRegularFile(filePath)) {
            return "[ERROR] Path is not a regular file: " + filePath;
        }

        if (!Files.isReadable(filePath)) {
            return "[ERROR] File is not readable: " + filePath;
        }

        try {
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return "[ERROR] File too large (%,d bytes). Maximum allowed is %,d bytes (100 KB): %s"
                        .formatted(fileSize, MAX_FILE_SIZE_BYTES, filePath);
            }

            // Add line numbers so the LLM can reference specific lines in its review
            var lines = Files.readAllLines(filePath);
            var sb = new StringBuilder();
            sb.append("File: ").append(filePath.getFileName()).append(" (").append(lines.size()).append(" lines)\n");
            for (int i = 0; i < lines.size(); i++) {
                sb.append("%4d: %s%n".formatted(i + 1, lines.get(i)));
            }
            return sb.toString();
        } catch (IOException e) {
            return "[ERROR] Failed to read file '%s': %s".formatted(filePath, e.getMessage());
        }
    }
}
