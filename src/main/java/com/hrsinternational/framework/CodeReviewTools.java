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
 * <p>All operations are delegated to the underlying tool classes, keeping
 * this wrapper thin and focused solely on framework integration.</p>
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

    /**
     * Reads the full contents of a source file from disk.
     *
     * @param path absolute or relative path to the file to read
     * @return the file contents as a string, or an error message if the file
     *         cannot be read
     */
    @Tool("Read the contents of a source file at the given path")
    public String readFile(@P("Absolute or relative path to the file") String path) {
        return readFileTool.execute(path);
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
        boolean recurse = Boolean.parseBoolean(recursive);
        return listDirectoryTool.execute(path, recurse);
    }

    /**
     * Runs style checks and basic static analysis on a single Java source file.
     *
     * @param path path to the Java source file to analyse
     * @return a formatted list of issues with line numbers and severity levels
     */
    @Tool("Run style checks and basic static analysis on a Java file. Returns list of issues found with line numbers and severity.")
    public String runLinter(@P("Path to the Java source file") String path) {
        return runLinterTool.execute(path);
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
        return writeReportTool.execute(path, content);
    }
}
