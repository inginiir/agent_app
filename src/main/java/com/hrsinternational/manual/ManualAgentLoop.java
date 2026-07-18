package com.hrsinternational.manual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.hrsinternational.tools.ToolRegistry;

import java.io.IOException;

/**
 * The core agentic loop for the AI Code Reviewer — the heart of Phase 1.
 *
 * <p>This class implements the "tool-calling loop" pattern:
 * <ol>
 *   <li>Send the conversation (system prompt + user message) to the LLM.</li>
 *   <li>If the LLM responds with tool calls, execute each tool and feed the
 *       results back as {@code tool} messages.</li>
 *   <li>Repeat until the LLM produces a final text response (no tool calls)
 *       or the maximum iteration count is reached.</li>
 * </ol>
 *
 * <p>The system prompt instructs the LLM to act as an expert Java code reviewer
 * that uses the available tools to discover, read, lint, and report on Java
 * source files.
 *
 * <p><strong>Enforcement:</strong> If the LLM attempts to finish without calling
 * {@code write_report}, the loop injects a correction message and forces the
 * agent back into the loop. This ensures the full workflow is always completed.
 */
public final class ManualAgentLoop {

    /** Maximum characters of a tool result to display in the console log. */
    private static final int RESULT_PREVIEW_LENGTH = 200;

    /** Maximum times the loop will try to force the agent to call write_report. */
    private static final int MAX_REENTRY_ATTEMPTS = 3;

    static final String SYSTEM_PROMPT = """
            You are an expert Java code reviewer. You MUST follow the exact workflow below.
            Do NOT skip any step. Do NOT hallucinate or guess file contents.
            You can ONLY know what is in a file by calling the read_file tool.

            MANDATORY WORKFLOW (follow this order strictly):

            STEP 1: Call list_directory with the directory path to discover all .java files.
            STEP 2: For EACH .java file found, call read_file to read its full source code.
            STEP 3: For EACH .java file found, call run_linter to get automated issue detection.
            STEP 4: Analyze all the code you have read for: naming conventions, error handling,
                     complexity, code smells, potential bugs, and design improvements.
            STEP 5: Call write_report to save a comprehensive markdown review report.
                     The report content MUST NOT be empty. It must contain your full analysis.

            RULES:
            - You MUST call list_directory FIRST before anything else.
            - You MUST call read_file on every file BEFORE writing the report.
            - You MUST call run_linter on every file BEFORE writing the report.
            - You MUST NOT call write_report until you have read and linted ALL files.
            - You MUST NOT invent or guess file names — only use names returned by list_directory.
            - You MUST NOT guess file contents — only use content returned by read_file.
            - The write_report content must include actual code quotes from the files you read.

            Report structure:
            - Executive summary
            - Per-file findings (with exact line numbers and code quotes from read_file output)
            - Severity ratings (CRITICAL / WARNING / INFO)
            - Concrete refactoring suggestions with improved code examples""";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final OllamaClient client;
    private final ToolRegistry registry;
    private final int maxIterations;

    /**
     * Creates a new agent loop.
     *
     * @param client        the Ollama HTTP client
     * @param registry      the tool registry for schema generation and dispatch
     * @param maxIterations the maximum number of LLM round-trips before aborting
     */
    public ManualAgentLoop(OllamaClient client, ToolRegistry registry, int maxIterations) {
        this.client = client;
        this.registry = registry;
        this.maxIterations = maxIterations;
    }

    /**
     * Creates a new agent loop with the default maximum iterations (20).
     *
     * @param client   the Ollama HTTP client
     * @param registry the tool registry
     */
    public ManualAgentLoop(OllamaClient client, ToolRegistry registry) {
        this(client, registry, 20);
    }

    /**
     * Runs the agentic loop with the given user message.
     *
     * <p>The loop sends the conversation to the LLM, processes any tool calls,
     * feeds results back, and repeats until the LLM produces a final text
     * response or the iteration limit is reached.
     *
     * <p>If the LLM attempts to finish without calling {@code write_report},
     * the loop injects a correction message and forces the agent to continue.
     *
     * @param userMessage the user's request (e.g., "Review the Java files in /path/to/project")
     * @return the LLM's final text response, or an error message if the loop
     *         exceeds the maximum iterations
     */
    public String run(String userMessage) {
        // Build initial messages array
        JsonArray messages = new JsonArray();

        // System message
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        // Tool schemas
        JsonArray toolSchemas = registry.getToolSchemas();

        // Track whether key tools were called successfully
        boolean reportWritten = false;
        boolean filesRead = false;
        int reentryAttempts = 0;

        // ── Agentic Loop ─────────────────────────────────────────────
        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            System.out.println("\n┌──────────────────────────────────────────────────────────────");
            System.out.println("│ 🔄 ITERATION " + iteration + " / " + maxIterations);
            System.out.println("└──────────────────────────────────────────────────────────────\n");

            JsonObject response;
            try {
                response = client.chat(messages, toolSchemas);
            } catch (IOException | InterruptedException e) {
                String error = "[ERROR] Failed to communicate with Ollama: " + e.getMessage();
                System.err.println(error);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return error;
            }

            // Extract the assistant's message
            JsonObject assistantMessage = response.getAsJsonObject("message");
            if (assistantMessage == null) {
                return "[ERROR] Ollama response missing 'message' field. Full response:\n"
                        + GSON.toJson(response);
            }

            // Add assistant message to conversation history
            messages.add(assistantMessage);

            // Check for tool calls
            if (assistantMessage.has("tool_calls")
                    && assistantMessage.getAsJsonArray("tool_calls") != null
                    && !assistantMessage.getAsJsonArray("tool_calls").isEmpty()) {

                JsonArray toolCalls = assistantMessage.getAsJsonArray("tool_calls");

                for (JsonElement toolCallElement : toolCalls) {
                    JsonObject toolCall = toolCallElement.getAsJsonObject();
                    JsonObject function = toolCall.getAsJsonObject("function");

                    String name = function.get("name").getAsString();
                    JsonObject arguments = function.getAsJsonObject("arguments");

                    // Execute the tool
                    String result = registry.execute(name, arguments);

                    // Track successful tool calls
                    if ("write_report".equals(name) && result.startsWith("[SUCCESS]")) {
                        reportWritten = true;
                    }
                    if ("read_file".equals(name) && !result.startsWith("[ERROR]")) {
                        filesRead = true;
                    }

                    // Build tool result message
                    JsonObject toolResultMsg = new JsonObject();
                    toolResultMsg.addProperty("role", "tool");
                    toolResultMsg.addProperty("content", result);
                    messages.add(toolResultMsg);

                    // Log the tool call
                    String argsPreview = arguments != null ? arguments.toString() : "{}";
                    String resultPreview = result.length() > RESULT_PREVIEW_LENGTH
                            ? result.substring(0, RESULT_PREVIEW_LENGTH) + "..."
                            : result;

                    System.out.println("[Iteration %d] Tool: %s | Args: %s | Result: %s"
                            .formatted(iteration, name, argsPreview, resultPreview));
                }

                // Continue the loop — LLM needs to process tool results
                continue;
            }

            // No tool calls — the LLM wants to finish
            String content = assistantMessage.has("content") && !assistantMessage.get("content").isJsonNull()
                    ? assistantMessage.get("content").getAsString()
                    : "";

            // ── Guard: force agent to call write_report ──────────────
            // Only enforce write_report if the model has actually read files
            // (otherwise we'd force a report before any analysis was done).
            if (!reportWritten && filesRead && !content.isBlank()) {
                reentryAttempts++;

                if (reentryAttempts <= MAX_REENTRY_ATTEMPTS) {
                    System.out.println("[Iteration %d] ⚠ Agent returned text without calling write_report — forcing re-entry (attempt %d/%d)..."
                            .formatted(iteration, reentryAttempts, MAX_REENTRY_ATTEMPTS));

                    JsonObject correction = new JsonObject();
                    correction.addProperty("role", "user");
                    correction.addProperty("content",
                            "You have not saved the report yet. You MUST call the write_report tool now "
                            + "with the full review content and the report path. Do NOT respond with text — "
                            + "call the write_report tool.");
                    messages.add(correction);
                    continue;
                } else {
                    // Model can't follow instructions — call write_report directly
                    System.out.println("[Iteration %d] ⚠ Max re-entry attempts reached — calling write_report directly..."
                            .formatted(iteration));
                    String reportPath = extractReportPath(userMessage);
                    String reportContent = content.replace("\\n", "\n");
                    String result = registry.execute("write_report",
                            buildWriteReportArgs(reportPath, reportContent));
                    System.out.println("[Iteration %d] Tool: write_report (forced) | Result: %s"
                            .formatted(iteration, result));
                    reportWritten = result.startsWith("[SUCCESS]");
                }
            }

            if (!content.isBlank()) {
                System.out.println("\n✅ Agent finished with final response.");
                return content;
            }

            // Edge case: no tool calls and no content — continue to prompt the LLM
            System.out.println("[Iteration %d] No tool calls and no content — prompting LLM to continue..."
                    .formatted(iteration));
        }

        // Max iterations reached
        String error = "[ERROR] Agent loop exceeded maximum iterations (%d). The review may be incomplete."
                .formatted(maxIterations);
        System.err.println(error);
        return error;
    }

    /**
     * Extracts the report output path from the user message.
     * Looks for the "Save the report to" pattern in the message.
     */
    private static String extractReportPath(String userMessage) {
        String marker = "Save the report to ";
        int idx = userMessage.indexOf(marker);
        if (idx >= 0) {
            return userMessage.substring(idx + marker.length()).strip();
        }
        return "output/reports/manual_review.md";
    }

    /**
     * Builds a JsonObject with path and content arguments for write_report.
     */
    private static JsonObject buildWriteReportArgs(String path, String content) {
        JsonObject args = new JsonObject();
        args.addProperty("path", path);
        args.addProperty("content", content);
        return args;
    }
}
