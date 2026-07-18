package com.hrsinternational.framework;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.hrsinternational.tools.ReadFileTool;
import com.hrsinternational.tools.ListDirectoryTool;
import com.hrsinternational.tools.RunLinterTool;
import com.hrsinternational.tools.WriteReportTool;

/**
 * LangChain4j-annotated tool wrapper for the AI Code Reviewer.
 *
 * <p>This class serves as the bridge between the LangChain4j framework's
 * tool-calling mechanism and the standalone tool implementations in
 * {@code com.hrsinternational.tools}. Each method is annotated with
 * {@link Tool} so that the framework can automatically generate JSON
 * tool schemas and dispatch calls from the LLM to the correct method.</p>
 *
 * <p>All tool calls are logged to standard output for visibility, since
 * LangChain4j's internal tool loop is otherwise silent.</p>
 *
 * @see ReadFileTool
 * @see ListDirectoryTool
 * @see RunLinterTool
 * @see WriteReportTool
 */
public class CodeReviewTools {

    private final ReadFileTool readFileTool = new ReadFileTool();
    private final ListDirectoryTool listDirectoryTool = new ListDirectoryTool();
    private final RunLinterTool runLinterTool = new RunLinterTool();
    private final WriteReportTool writeReportTool = new WriteReportTool();

    /** Tracks whether writeReport was called successfully during this session. */
    private boolean reportWritten = false;

    /** Tracks whether at least one file was read successfully. */
    private boolean filesRead = false;

    /** Tracks whether runLinter was called on at least one file. */
    private boolean linterRun = false;

    /** Counts total tool invocations for logging. */
    private int callCount = 0;

    /**
     * Returns whether the {@code writeReport} tool was called successfully.
     *
     * @return {@code true} if a report was saved via the tool
     */
    public boolean isReportWritten() {
        return reportWritten;
    }

    /**
     * Reads the full contents of a source file from disk.
     *
     * @param path absolute or relative path to the file to read
     * @return the file contents as a string, or an error message if the file
     *         cannot be read
     */
    @Tool("Read the contents of a source file at the given path")
    public String readFile(@P("Absolute or relative path to the file") String path) {
        logToolCall("readFile", path);
        String result = readFileTool.execute(path);
        if (!result.startsWith("[ERROR]")) {
            filesRead = true;
        }
        logToolResult(result);
        return result;
    }

    /**
     * Lists all {@code .java} files in the specified directory.
     *
     * <p>The {@code recursive} parameter accepts a string ("true"/"false") because
     * smaller LLMs often send boolean values as strings, which LangChain4j's
     * argument coercion cannot convert to primitive {@code boolean}.</p>
     *
     * @param path      path to the directory to scan
     * @param recursive whether to descend into subdirectories ("true" or "false")
     * @return a newline-separated list of {@code .java} file paths found
     */
    @Tool("List all files in a directory, optionally recursive. Returns newline-separated list of .java file paths.")
    public String listDirectory(
            @P("Path to the directory") String path,
            @P("Whether to list recursively - true or false, default false") String recursive) {
        logToolCall("listDirectory", path + " (recursive=" + recursive + ")");
        boolean recurse = Boolean.parseBoolean(recursive);
        String result = listDirectoryTool.execute(path, recurse);
        logToolResult(result);
        return result;
    }

    /**
     * Runs style checks and basic static analysis on a single Java source file.
     *
     * @param path path to the Java source file to analyse
     * @return a formatted list of issues with line numbers and severity levels
     */
    @Tool("Run style checks and basic static analysis on a Java file. Returns list of issues found with line numbers and severity.")
    public String runLinter(@P("Path to the Java source file") String path) {
        logToolCall("runLinter", path);
        String result = runLinterTool.execute(path);
        if (!result.startsWith("[ERROR]")) {
            linterRun = true;
        }
        logToolResult(result);
        return result;
    }

    /**
     * Persists the code review report as a Markdown file on disk.
     *
     * @param path    output file path for the report (will be created or overwritten)
     * @param content Markdown content of the review report
     * @return a confirmation message indicating success, or an error message
     */
    @Tool("Save the code review report as a markdown file to disk")
    public String writeReport(
            @P("Output file path for the report") String path,
            @P("Markdown content of the review report") String content) {
        logToolCall("writeReport", path);
        // Guard: reject report if files were not read or linter was not run
        if (!filesRead) {
            String error = "[ERROR] You must call readFile on the source files BEFORE writing the report. "
                    + "Call listDirectory first, then readFile on each file, then runLinter on each file.";
            logToolResult(error);
            return error;
        }
        if (!linterRun) {
            String error = "[ERROR] You must call runLinter on the source files BEFORE writing the report. "
                    + "Call runLinter on each .java file you read, then call writeReport.";
            logToolResult(error);
            return error;
        }
        // Smaller models sometimes double-escape newlines as literal \n
        if (content != null) {
            content = content.replace("\\n", "\n");
        }
        String result = writeReportTool.execute(path, content);
        if (result.startsWith("[SUCCESS]")) {
            reportWritten = true;
        }
        logToolResult(result);
        return result;
    }

    // ── Logging helpers ──────────────────────────────────────────────

    private void logToolCall(String toolName, String args) {
        callCount++;
        System.out.printf("  [Tool #%d] %s(%s)%n", callCount, toolName, args);
    }

    private void logToolResult(String result) {
        String preview = result.length() > 150
                ? result.substring(0, 150) + "..."
                : result;
        System.out.printf("           → %s%n", preview.replace("\n", " "));
    }
}
