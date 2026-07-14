package com.hrsinternational.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Central registry for all tools available to the AI Code Reviewer agent.
 *
 * <p>This registry serves two purposes:
 * <ol>
 *   <li><strong>Schema generation</strong> — provides JSON tool schemas in the
 *       format expected by Ollama's native {@code /api/chat} endpoint for
 *       function-calling.</li>
 *   <li><strong>Dispatch</strong> — routes tool calls from the LLM to the
 *       correct tool implementation, extracting arguments from the
 *       {@link JsonObject} payload.</li>
 * </ol>
 *
 * <p>Registered tools:
 * <ul>
 *   <li>{@code read_file} — {@link ReadFileTool}</li>
 *   <li>{@code list_directory} — {@link ListDirectoryTool}</li>
 *   <li>{@code run_linter} — {@link RunLinterTool}</li>
 *   <li>{@code write_report} — {@link WriteReportTool}</li>
 * </ul>
 */
public final class ToolRegistry {

    private final ReadFileTool readFileTool = new ReadFileTool();
    private final ListDirectoryTool listDirectoryTool = new ListDirectoryTool();
    private final RunLinterTool runLinterTool = new RunLinterTool();
    private final WriteReportTool writeReportTool = new WriteReportTool();

    /**
     * Returns the JSON tool schemas array for Ollama's native API.
     *
     * <p>The returned array conforms to the tool-calling schema:
     * <pre>{@code
     * [
     *   {
     *     "type": "function",
     *     "function": {
     *       "name": "tool_name",
     *       "description": "...",
     *       "parameters": {
     *         "type": "object",
     *         "properties": { ... },
     *         "required": [ ... ]
     *       }
     *     }
     *   }
     * ]
     * }</pre>
     *
     * @return a {@link JsonArray} of tool schemas
     */
    public JsonArray getToolSchemas() {
        JsonArray tools = new JsonArray();
        tools.add(buildReadFileSchema());
        tools.add(buildListDirectorySchema());
        tools.add(buildRunLinterSchema());
        tools.add(buildWriteReportSchema());
        return tools;
    }

    /**
     * Dispatches a tool call to the appropriate tool implementation.
     *
     * @param toolName  the name of the tool to execute (e.g., {@code "read_file"})
     * @param arguments a {@link JsonObject} containing the tool's arguments
     * @return the result string from the tool execution, or an error message
     *         if the tool name is unrecognized or arguments are missing
     */
    public String execute(String toolName, JsonObject arguments) {
        if (toolName == null || toolName.isBlank()) {
            return "[ERROR] Tool name must not be null or blank.";
        }
        if (arguments == null) {
            arguments = new JsonObject();
        }

        return switch (toolName) {
            case "read_file" -> {
                String path = getRequiredString(arguments, "path");
                if (path == null) yield "[ERROR] Missing required argument 'path' for read_file.";
                yield readFileTool.execute(path);
            }
            case "list_directory" -> {
                String path = getRequiredString(arguments, "path");
                if (path == null) yield "[ERROR] Missing required argument 'path' for list_directory.";
                boolean recursive = arguments.has("recursive")
                        && arguments.get("recursive").getAsBoolean();
                yield listDirectoryTool.execute(path, recursive);
            }
            case "run_linter" -> {
                String path = getRequiredString(arguments, "path");
                if (path == null) yield "[ERROR] Missing required argument 'path' for run_linter.";
                yield runLinterTool.execute(path);
            }
            case "write_report" -> {
                String path = getRequiredString(arguments, "path");
                if (path == null) yield "[ERROR] Missing required argument 'path' for write_report.";
                String content = getRequiredString(arguments, "content");
                if (content == null) yield "[ERROR] Missing required argument 'content' for write_report.";
                yield writeReportTool.execute(path, content);
            }
            default -> "[ERROR] Unknown tool: '%s'. Available tools: read_file, list_directory, run_linter, write_report."
                    .formatted(toolName);
        };
    }

    // ── Schema Builders ──────────────────────────────────────────────

    private JsonObject buildReadFileSchema() {
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute or relative path to the file");

        JsonObject properties = new JsonObject();
        properties.add("path", pathProp);

        JsonArray required = new JsonArray();
        required.add("path");

        return buildToolSchema(
                "read_file",
                "Read the contents of a source file at the given path",
                properties,
                required
        );
    }

    private JsonObject buildListDirectorySchema() {
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute or relative path to the directory to list");

        JsonObject recursiveProp = new JsonObject();
        recursiveProp.addProperty("type", "boolean");
        recursiveProp.addProperty("description", "If true, recursively list all subdirectories. Defaults to false.");

        JsonObject properties = new JsonObject();
        properties.add("path", pathProp);
        properties.add("recursive", recursiveProp);

        JsonArray required = new JsonArray();
        required.add("path");

        return buildToolSchema(
                "list_directory",
                "List all Java source files in a directory",
                properties,
                required
        );
    }

    private JsonObject buildRunLinterSchema() {
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute or relative path to the Java file to lint");

        JsonObject properties = new JsonObject();
        properties.add("path", pathProp);

        JsonArray required = new JsonArray();
        required.add("path");

        return buildToolSchema(
                "run_linter",
                "Run style and quality checks on a Java source file and return issues with line numbers",
                properties,
                required
        );
    }

    private JsonObject buildWriteReportSchema() {
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "Absolute or relative path where the report file should be saved");

        JsonObject contentProp = new JsonObject();
        contentProp.addProperty("type", "string");
        contentProp.addProperty("description", "The markdown content of the review report");

        JsonObject properties = new JsonObject();
        properties.add("path", pathProp);
        properties.add("content", contentProp);

        JsonArray required = new JsonArray();
        required.add("path");
        required.add("content");

        return buildToolSchema(
                "write_report",
                "Write a code review report to a file at the given path",
                properties,
                required
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Builds a single tool schema in Ollama's expected format.
     */
    private JsonObject buildToolSchema(String name, String description,
                                       JsonObject properties, JsonArray required) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        parameters.add("required", required);

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);

        return tool;
    }

    /**
     * Safely extracts a required string argument from a {@link JsonObject}.
     *
     * @return the string value, or {@code null} if the key is missing or not a string
     */
    private String getRequiredString(JsonObject arguments, String key) {
        if (!arguments.has(key) || arguments.get(key).isJsonNull()) {
            return null;
        }
        return arguments.get(key).getAsString();
    }
}
