package com.hrsinternational.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tool for writing content to a file on disk.
 *
 * <p>This tool is primarily used by the LLM agent to save code review reports,
 * but can write any text content to any writable path. It automatically creates
 * parent directories if they do not already exist.
 *
 * <p><strong>Error handling policy:</strong> This tool never throws exceptions.
 * All error conditions are returned as descriptive error strings so the LLM
 * can reason about failures and decide how to proceed.
 */
public final class WriteReportTool {

    /**
     * Writes the given content to the specified file path.
     *
     * <p>If the parent directories of the target path do not exist, they are
     * created automatically. If the file already exists, it is overwritten.
     *
     * @param path    the absolute or relative path where the file should be written
     * @param content the text content to write to the file
     * @return a confirmation message with the absolute path of the written file,
     *         or an error message if the write fails
     */
    public String execute(String path, String content) {
        if (path == null || path.isBlank()) {
            return "[ERROR] Path must not be null or blank.";
        }
        if (content == null || content.isBlank()) {
            return "[ERROR] Content must not be null or empty. You must first read and analyze "
                    + "the source files using read_file and run_linter before writing the report.";
        }

        Path filePath;
        try {
            filePath = Path.of(path).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "[ERROR] Invalid path: " + e.getMessage();
        }

        try {
            // Create parent directories if they don't exist
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(filePath, content);
            return "[SUCCESS] Report written to: " + filePath;
        } catch (IOException e) {
            return "[ERROR] Failed to write file '%s': %s".formatted(filePath, e.getMessage());
        }
    }
}
