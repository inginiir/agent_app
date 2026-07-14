package com.hrsinternational.framework;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j {@code AiService} interface for the AI Code Reviewer.
 *
 * <p>The LangChain4j framework creates a dynamic proxy implementation of this
 * interface that handles:</p>
 * <ul>
 *   <li><strong>Tool schema generation</strong> — automatically introspects
 *       {@link CodeReviewTools} and builds JSON function-calling schemas.</li>
 *   <li><strong>API calls to Ollama</strong> — sends the system and user
 *       messages to the configured local model.</li>
 *   <li><strong>Agentic tool loop</strong> — when the model responds with a
 *       tool-call request, the framework dispatches the call, feeds the result
 *       back, and re-invokes the model until it produces a final text
 *       response.</li>
 * </ul>
 *
 * <p>This declarative approach eliminates the need for a manual
 * request → parse → dispatch → re-invoke loop, which was implemented
 * by hand in Phase&nbsp;1.</p>
 *
 * @see CodeReviewTools
 * @see FrameworkAgentMain
 */
public interface CodeReviewAssistant {

    /**
     * Reviews Java source files according to the system prompt workflow.
     *
     * <p>The system prompt instructs the model to:</p>
     * <ol>
     *   <li>Discover all {@code .java} files via {@code listDirectory}.</li>
     *   <li>Read each file via {@code readFile}.</li>
     *   <li>Run automated checks via {@code runLinter}.</li>
     *   <li>Analyse the code for quality issues.</li>
     *   <li>Persist a comprehensive Markdown report via {@code writeReport}.</li>
     * </ol>
     *
     * @param request a natural-language instruction describing which directory
     *                to review and where to save the report
     * @return the final review summary produced by the model after all tool
     *         calls have been executed
     */
    @SystemMessage("""
            You are an expert Java code reviewer with deep knowledge of clean code principles,
            SOLID design patterns, and Java best practices.

            Your task: Review the Java source files in the provided project directory.

            Workflow:
            1. Use listDirectory to discover all .java files in the project.
            2. Use readFile to read each source file.
            3. Use runLinter to get automated style/issue feedback on each file.
            4. Analyze the code for: naming conventions, error handling, complexity,
               code smells, potential bugs, and design improvements.
            5. Use writeReport to save a comprehensive markdown review report.

            Report structure:
            - Executive summary
            - Per-file findings (with line numbers and code quotes)
            - Severity ratings (CRITICAL / WARNING / INFO)
            - Concrete refactoring suggestions with code examples

            Be specific: reference exact line numbers, quote problematic code,
            and provide concrete improvement suggestions.
            """)
    String review(@UserMessage String request);
}
