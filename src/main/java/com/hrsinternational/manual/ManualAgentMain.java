package com.hrsinternational.manual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hrsinternational.tools.ToolRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Entry point for Phase 1 of the AI Code Reviewer agent.
 *
 * <p>This class bootstraps the manual agentic loop by:
 * <ol>
 *   <li>Loading configuration from {@code config.properties} on the classpath.</li>
 *   <li>Resolving the {@code samples/} directory and report output directory.</li>
 *   <li>Creating the {@link OllamaClient}, {@link ToolRegistry}, and {@link ManualAgentLoop}.</li>
 *   <li>Running the agent with a review request targeting the sample files.</li>
 *   <li>Printing the final result and elapsed time.</li>
 * </ol>
 */
public final class ManualAgentMain {

    /** Configuration file loaded from the classpath. */
    private static final String CONFIG_FILE = "config.properties";

    /**
     * Main entry point.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        // ── Banner ───────────────────────────────────────────────────
        System.out.println("============================================");
        System.out.println("  AI Code Reviewer — Phase 1 (Manual Loop)");
        System.out.println("============================================");
        System.out.println();

        // ── Load Configuration ───────────────────────────────────────
        Properties config = loadConfig();
        String baseUrl = config.getProperty("ollama.base.url", "http://localhost:11434");
        String model = config.getProperty("ollama.model", "llama3.1");
        int maxIterations = Integer.parseInt(
                config.getProperty("agent.max.iterations", "20")
        );
        String reportOutputDir = config.getProperty("report.output.dir", "output/reports");

        System.out.println("Configuration:");
        System.out.println("  Ollama URL:      " + baseUrl);
        System.out.println("  Model:           " + model);
        System.out.println("  Max iterations:  " + maxIterations);
        System.out.println("  Report output:   " + reportOutputDir);
        System.out.println();

        // ── Resolve Paths ────────────────────────────────────────────
        Path samplesPath = Path.of(System.getProperty("user.dir"), "samples").toAbsolutePath();
        Path outputPath = Path.of(System.getProperty("user.dir"), reportOutputDir).toAbsolutePath();

        System.out.println("Resolved paths:");
        System.out.println("  Samples directory: " + samplesPath);
        System.out.println("  Output directory:  " + outputPath);
        System.out.println();

        // Verify samples directory exists
        if (!Files.isDirectory(samplesPath)) {
            System.err.println("[ERROR] Samples directory does not exist: " + samplesPath);
            System.err.println("Please create a 'samples/' directory with Java source files to review.");
            System.exit(1);
        }

        // Create output directory if needed
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to create output directory: " + e.getMessage());
            System.exit(1);
        }

        // ── Create Components ────────────────────────────────────────
        OllamaClient client = new OllamaClient(baseUrl, model);
        ToolRegistry registry = new ToolRegistry();
        ManualAgentLoop agentLoop = new ManualAgentLoop(client, registry, maxIterations);

        // ── Run the Agent ────────────────────────────────────────────
        String reportPath = outputPath.resolve("manual_review.md").toString();
        String userMessage = "Review the Java source files in the %s directory. Save the report to %s"
                .formatted(samplesPath, reportPath);

        System.out.println("User message:");
        System.out.println("  " + userMessage);
        System.out.println();
        System.out.println("Starting agent loop...");
        System.out.println("════════════════════════════════════════════════════════════════\n");

        long startTime = System.nanoTime();

        String result = agentLoop.run(userMessage);

        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        // ── Print Results ────────────────────────────────────────────
        System.out.println("\n════════════════════════════════════════════════════════════════");
        System.out.println("AGENT RESULT:");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println(result);
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.printf("⏱ Elapsed time: %.2f seconds%n", elapsedSeconds);
        System.out.println("════════════════════════════════════════════════════════════════");

        // ── Fallback: save report if LLM returned it as text ─────────
        // Smaller models sometimes return the review as their final text
        // response instead of calling write_report. They may also return it
        // as a JSON-formatted tool call in text. Detect both cases and save.
        Path reportFile = Path.of(reportPath);
        boolean reportMissing = !Files.exists(reportFile) || isFileEmpty(reportFile);
        if (reportMissing && result != null && !result.isBlank() && !result.startsWith("[ERROR]")) {
            String reportContent = extractReportContent(result);
            try {
                Files.createDirectories(reportFile.getParent());
                Files.writeString(reportFile, reportContent);
                System.out.println("\n📄 Report saved (fallback): " + reportFile.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[WARN] Failed to save fallback report: " + e.getMessage());
            }
        }
    }

    /**
     * Loads configuration from {@code config.properties} on the classpath.
     *
     * @return the loaded {@link Properties}, or defaults if the file is not found
     */
    private static Properties loadConfig() {
        Properties config = new Properties();
        try (InputStream is = ManualAgentMain.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                System.out.println("[WARN] Configuration file '%s' not found on classpath. Using defaults."
                        .formatted(CONFIG_FILE));
                return config;
            }
            config.load(is);
        } catch (IOException e) {
            System.err.println("[WARN] Failed to load configuration: " + e.getMessage());
        }
        return config;
    }

    /**
     * Checks whether an existing file is empty or contains only whitespace.
     *
     * @param file the file to check
     * @return {@code true} if the file is empty or whitespace-only
     */
    private static boolean isFileEmpty(Path file) {
        try {
            return Files.size(file) == 0 || Files.readString(file).isBlank();
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Extracts the actual report content from the LLM's response.
     *
     * <p>Smaller models sometimes return the write_report tool call as JSON text
     * like {@code {"name": "write_report", "parameters": {"content": "...", "path": "..."}}}.
     * This method detects that pattern and extracts the {@code content} field.
     * If the text is already plain markdown, it is returned as-is.
     *
     * @param result the raw text from the LLM's final response
     * @return the extracted markdown content
     */
    private static String extractReportContent(String result) {
        String trimmed = result.strip();
        if (trimmed.startsWith("{") && trimmed.contains("write_report")) {
            try {
                JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
                JsonObject params = json.has("parameters")
                        ? json.getAsJsonObject("parameters")
                        : json;
                if (params.has("content")) {
                    String content = params.get("content").getAsString();
                    if (!content.isBlank()) {
                        return content;
                    }
                }
            } catch (Exception ignored) {
                // Not valid JSON — fall through to return as-is
            }
        }
        return result;
    }
}
