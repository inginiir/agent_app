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
            You are an expert Java code reviewer. You MUST follow the exact workflow below.
            Do NOT skip any step. Do NOT hallucinate or guess file contents.
            You can ONLY know what is in a file by calling the readFile tool.

            MANDATORY WORKFLOW (follow this order strictly):

            STEP 1: Call listDirectory with the directory path to discover all .java files.
            STEP 2: For EACH .java file found, call readFile to read its full source code.
            STEP 3: For EACH .java file found, call runLinter to get automated issue detection.
            STEP 4: Analyze all the code you have read for: naming conventions, error handling,
                     complexity, code smells, potential bugs, and design improvements.
            STEP 5: Call writeReport to save a comprehensive markdown review report.
                     The report content MUST NOT be empty. It must contain your full analysis.

            RULES:
            - You MUST call listDirectory FIRST before anything else.
            - You MUST call readFile on every file BEFORE writing the report.
            - You MUST call runLinter on every file BEFORE writing the report.
            - You MUST NOT call writeReport until you have read and linted ALL files.
            - You MUST NOT invent or guess file names — only use names returned by listDirectory.
            - You MUST NOT guess file contents — only use content returned by readFile.
            - The writeReport content must include actual code quotes from the files you read.

            Report structure:
            - Executive summary
            - Per-file findings (with exact line numbers and code quotes from readFile output)
            - Severity ratings (CRITICAL / WARNING / INFO)
            - Concrete refactoring suggestions with improved code examples
            """)
    String review(@UserMessage String request);
}
