package com.hrsinternational.framework;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Entry point for Phase&nbsp;2 of the AI Code Reviewer.
 *
 * <p>This class bootstraps the LangChain4j-based agent by:</p>
 * <ol>
 *   <li>Loading configuration from {@code config.properties} on the classpath.</li>
 *   <li>Resolving the sample-source and report-output directories relative to
 *       the current working directory.</li>
 *   <li>Building an {@link OllamaChatModel} connected to the local Ollama
 *       instance.</li>
 *   <li>Creating a {@link CodeReviewAssistant} proxy via {@link AiServices}
 *       with {@link CodeReviewTools} registered.</li>
 *   <li>Invoking the review and printing the result together with elapsed
 *       time.</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <p>The following keys are read from {@code config.properties}:</p>
 * <table>
 *   <caption>Supported configuration properties</caption>
 *   <tr><th>Key</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>{@code ollama.base.url}</td><td>{@code http://localhost:11434}</td>
 *       <td>Base URL of the Ollama server</td></tr>
 *   <tr><td>{@code ollama.model}</td><td>{@code qwen2.5-coder:14b}</td>
 *       <td>Model identifier to use for inference</td></tr>
 *   <tr><td>{@code report.output.dir}</td><td>{@code output/reports}</td>
 *       <td>Directory where review reports are written</td></tr>
 * </table>
 *
 * @see CodeReviewAssistant
 * @see CodeReviewTools
 */
public class FrameworkAgentMain {

    /** Classpath resource name for application configuration. */
    private static final String CONFIG_RESOURCE = "config.properties";

    /** Default Ollama server URL when not configured. */
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    /** Default model name when not configured. */
    private static final String DEFAULT_MODEL = "qwen2.5-coder:14b";

    /** Default report output directory relative to the working directory. */
    private static final String DEFAULT_REPORT_DIR = "output/reports";

    /**
     * Application entry point.
     *
     * @param args command-line arguments (currently unused)
     */
    static void main(String[] args) {
        printBanner();

        // ── 1. Load configuration ──────────────────────────────────────
        Properties config = loadConfig();
        String baseUrl = config.getProperty("ollama.base.url", DEFAULT_BASE_URL);
        String modelName = config.getProperty("ollama.model", DEFAULT_MODEL);
        String reportDir = config.getProperty("report.output.dir", DEFAULT_REPORT_DIR);

        // ── 2. Resolve paths ───────────────────────────────────────────
        String workingDir = System.getProperty("user.dir");
        Path samplesPath = Path.of(workingDir, "samples").toAbsolutePath().normalize();
        Path outputPath = Path.of(workingDir, reportDir).toAbsolutePath().normalize();

        System.out.println("Configuration:");
        System.out.println("  Ollama URL  : " + baseUrl);
        System.out.println("  Model       : " + modelName);
        System.out.println("  Samples dir : " + samplesPath);
        System.out.println("  Output dir  : " + outputPath);
        System.out.println();

        // ── 3. Create output directory if needed ───────────────────────
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            System.err.println("ERROR: Could not create output directory: " + outputPath);
            e.printStackTrace();
            System.exit(1);
        }

        // ── 4. Build the Ollama chat model ─────────────────────────────
        System.out.println("Connecting to Ollama at " + baseUrl + " ...");
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(5))
                .build();

        // ── 5. Build the AiService proxy with tools ────────────────────
        CodeReviewAssistant assistant = AiServices.builder(CodeReviewAssistant.class)
                .chatModel(model)
                .tools(new CodeReviewTools())
                .build();

        // ── 6. Run the review ──────────────────────────────────────────
        String userMessage = "Review the Java source files in " + samplesPath
                + ". Save the report to " + outputPath + "/framework_review.md";

        System.out.println("Sending review request to the model...");
        System.out.println("  → " + userMessage);
        System.out.println();

        long startTime = System.nanoTime();

        try {
            String result = assistant.review(userMessage);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            double elapsedSec = elapsedMs / 1_000.0;

            System.out.println("============================================");
            System.out.println("  Review Complete");
            System.out.println("============================================");
            System.out.println();
            System.out.println(result);
            System.out.println();
            System.out.printf("Elapsed time: %.2f seconds%n", elapsedSec);
            System.out.println();
            System.out.println("--------------------------------------------");
            System.out.println("NOTE: Compare this with Phase 1 — the manual");
            System.out.println("agent loop is no longer needed. LangChain4j's");
            System.out.println("AiServices handled the entire tool-calling");
            System.out.println("loop automatically: schema generation, JSON");
            System.out.println("parsing, tool dispatch, and re-invocation");
            System.out.println("were all managed by the framework.");
            System.out.println("--------------------------------------------");

            // Fallback: save report if LLM returned it as text instead of calling writeReport
            Path reportFile = Path.of(outputPath.toString(), "framework_review.md");
            boolean reportMissing = !Files.exists(reportFile) || Files.size(reportFile) == 0;
            if (reportMissing && result != null && !result.isBlank()) {
                Files.createDirectories(reportFile.getParent());
                Files.writeString(reportFile, result);
                System.out.println("\n📄 Report saved (fallback): " + reportFile.toAbsolutePath());
            }
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            double elapsedSec = elapsedMs / 1_000.0;

            System.err.println("============================================");
            System.err.println("  Review Failed");
            System.err.println("============================================");
            System.err.printf("Elapsed time before failure: %.2f seconds%n", elapsedSec);
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Prints the application startup banner to standard output.
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("============================================");
        System.out.println("  AI Code Reviewer — Phase 2 (LangChain4j)");
        System.out.println("============================================");
        System.out.println();
    }

    /**
     * Loads configuration from {@value #CONFIG_RESOURCE} on the classpath.
     *
     * <p>If the resource cannot be found, a warning is printed and an empty
     * {@link Properties} instance is returned so that defaults are used.</p>
     *
     * @return the loaded properties, never {@code null}
     */
    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = FrameworkAgentMain.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                System.out.println("WARNING: " + CONFIG_RESOURCE
                        + " not found on classpath — using defaults.");
            } else {
                props.load(in);
                System.out.println("Loaded configuration from " + CONFIG_RESOURCE);
            }
        } catch (IOException e) {
            System.err.println("WARNING: Failed to read " + CONFIG_RESOURCE
                    + " — using defaults.");
            e.printStackTrace();
        }
        return props;
    }
}
