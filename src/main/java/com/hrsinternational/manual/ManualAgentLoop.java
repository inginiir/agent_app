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
 */
public final class ManualAgentLoop {

    /** Maximum characters of a tool result to display in the console log. */
    private static final int RESULT_PREVIEW_LENGTH = 200;

    /** The system prompt that defines the agent's behavior and workflow. */
    static final String SYSTEM_PROMPT = """
            You are an expert Java code reviewer with deep knowledge of clean code principles,
            SOLID design patterns, and Java best practices.

            Your task: Review the Java source files in the provided project directory.

            Workflow:
            1. Use list_directory to discover all .java files in the project.
            2. Use read_file to read each source file.
            3. Use run_linter to get automated style/issue feedback on each file.
            4. Analyze the code for: naming conventions, error handling, complexity,
               code smells, potential bugs, and design improvements.
            5. Use write_report to save a comprehensive markdown review report.

            Report structure:
            - Executive summary
            - Per-file findings (with line numbers and code quotes)
            - Severity ratings (CRITICAL / WARNING / INFO)
            - Concrete refactoring suggestions with code examples

            Be specific: reference exact line numbers, quote problematic code,
            and provide concrete improvement suggestions.""";

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

            // No tool calls — this is the final response
            String content = assistantMessage.has("content") && !assistantMessage.get("content").isJsonNull()
                    ? assistantMessage.get("content").getAsString()
                    : "";

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
}
