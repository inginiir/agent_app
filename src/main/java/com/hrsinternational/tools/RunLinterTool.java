package com.hrsinternational.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two-tier Java source code linter tool.
 *
 * <p><strong>Tier 1 — Regex-based style checks:</strong>
 * <ul>
 *   <li>Lines longer than 120 characters</li>
 *   <li>Empty catch blocks (catch with empty or comment-only body)</li>
 *   <li>{@code System.out.println} / {@code System.err.println} usage</li>
 *   <li>Wildcard imports ({@code import java.util.*})</li>
 *   <li>Magic numbers (numeric literals other than 0, 1, -1 outside strings/comments)</li>
 *   <li>Missing Javadoc on public methods and classes</li>
 *   <li>Mutable public static non-final fields</li>
 *   <li>Raw types (e.g., {@code List} without generics)</li>
 * </ul>
 *
 * <p><strong>Tier 2 — Optional {@code javac -Xlint}:</strong>
 * Attempts to run {@code javac -Xlint:all} on the file for additional compiler
 * warnings. If {@code javac} is not available or the compilation fails, this tier
 * is skipped gracefully with a note.
 *
 * <p><strong>Output format:</strong> Each issue is reported on its own line as:
 * {@code [SEVERITY] Line N: description}
 *
 * <p><strong>Error handling policy:</strong> This tool never throws exceptions.
 */
public final class RunLinterTool {

    // ── Regex patterns for Tier 1 checks ──────────────────────────────

    private static final Pattern SYSTEM_OUT_PATTERN =
            Pattern.compile("System\\.(out|err)\\.println");

    private static final Pattern WILDCARD_IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+[\\w.]+\\.\\*\\s*;");

    private static final Pattern EMPTY_CATCH_PATTERN =
            Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*(//[^\n]*\\s*)*\\}");

    private static final Pattern MAGIC_NUMBER_PATTERN =
            Pattern.compile("(?<!\\w)(-?\\d+\\.?\\d*[fFdDlL]?)(?!\\w)");

    private static final Pattern PUBLIC_METHOD_PATTERN =
            Pattern.compile("^\\s*public\\s+(?!class|interface|enum|record|abstract)\\S+.*\\(.*\\)\\s*\\{?\\s*$");

    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("^\\s*public\\s+(class|interface|enum|record)\\s+");

    private static final Pattern MUTABLE_PUBLIC_STATIC_PATTERN =
            Pattern.compile("^\\s*public\\s+static\\s+(?!final\\b)\\S+\\s+\\w+");

    private static final Pattern RAW_TYPE_PATTERN =
            Pattern.compile("\\b(List|Set|Map|Collection|Iterable|Iterator|Queue|Deque|Optional)\\s+[a-z]\\w*");

    private static final Pattern JAVADOC_START_PATTERN =
            Pattern.compile("^\\s*/\\*\\*");

    private static final Pattern STRING_LITERAL_PATTERN =
            Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    private static final Pattern LINE_COMMENT_PATTERN =
            Pattern.compile("//.*$");

    /**
     * Runs the two-tier linter on the specified Java source file.
     *
     * @param path the absolute or relative path to the Java file to lint
     * @return a multi-line string of issues in the format
     *         {@code [SEVERITY] Line N: description}, or an error message
     *         if the file cannot be read
     */
    public String execute(String path) {
        if (path == null || path.isBlank()) {
            return "[ERROR] Path must not be null or blank.";
        }

        Path filePath;
        try {
            filePath = Path.of(path).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "[ERROR] Invalid path: " + e.getMessage();
        }

        if (!Files.exists(filePath)) {
            return "[ERROR] File does not exist: " + filePath;
        }

        if (!Files.isRegularFile(filePath)) {
            return "[ERROR] Path is not a regular file: " + filePath;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(filePath);
        } catch (IOException e) {
            return "[ERROR] Failed to read file '%s': %s".formatted(filePath, e.getMessage());
        }

        List<String> issues = new ArrayList<>();

        // ── Tier 1: Regex-based style checks ───────────────────────────
        runRegexChecks(lines, issues);

        // ── Tier 2: Optional javac -Xlint ──────────────────────────────
        runJavacLint(filePath, issues);

        if (issues.isEmpty()) {
            return "[INFO] No issues found in: " + filePath;
        }

        return String.join("\n", issues);
    }

    // ── Tier 1 Implementation ────────────────────────────────────────

    private void runRegexChecks(List<String> lines, List<String> issues) {
        boolean previousLineIsJavadoc = false;

        for (int i = 0; i < lines.size(); i++) {
            int lineNum = i + 1;
            String line = lines.get(i);

            // Track if the previous logical block had Javadoc
            if (JAVADOC_START_PATTERN.matcher(line).find()) {
                previousLineIsJavadoc = true;
            }

            // Check 1: Line length > 120 characters
            if (line.length() > 120) {
                issues.add("[WARNING] Line %d: Line is %d characters long (max 120)."
                        .formatted(lineNum, line.length()));
            }

            // Check 2: System.out.println / System.err.println
            if (SYSTEM_OUT_PATTERN.matcher(line).find()) {
                issues.add("[WARNING] Line %d: Use a logging framework instead of System.out/err.println."
                        .formatted(lineNum));
            }

            // Check 3: Wildcard imports
            if (WILDCARD_IMPORT_PATTERN.matcher(line).find()) {
                issues.add("[WARNING] Line %d: Avoid wildcard imports — use explicit imports."
                        .formatted(lineNum));
            }

            // Check 4: Missing Javadoc on public classes
            if (PUBLIC_CLASS_PATTERN.matcher(line).find()) {
                if (!previousLineIsJavadoc && !hasJavadocAbove(lines, i)) {
                    issues.add("[WARNING] Line %d: Public class/interface/enum is missing Javadoc."
                            .formatted(lineNum));
                }
                previousLineIsJavadoc = false;
            }

            // Check 5: Missing Javadoc on public methods
            if (PUBLIC_METHOD_PATTERN.matcher(line).find()) {
                if (!previousLineIsJavadoc && !hasJavadocAbove(lines, i)) {
                    issues.add("[WARNING] Line %d: Public method is missing Javadoc."
                            .formatted(lineNum));
                }
                previousLineIsJavadoc = false;
            }

            // Check 6: Mutable public static non-final fields
            if (MUTABLE_PUBLIC_STATIC_PATTERN.matcher(line).find()) {
                issues.add("[WARNING] Line %d: Public static field should be final — mutable global state is a code smell."
                        .formatted(lineNum));
            }

            // Check 7: Raw types
            String strippedLine = stripStringsAndComments(line);
            if (RAW_TYPE_PATTERN.matcher(strippedLine).find()) {
                issues.add("[WARNING] Line %d: Raw type usage detected — use generics (e.g., List<String> instead of List)."
                        .formatted(lineNum));
            }

            // Check 8: Magic numbers (outside of strings and comments)
            checkMagicNumbers(strippedLine, lineNum, issues);

            // Reset Javadoc flag if line is not a Javadoc end or blank
            if (!line.isBlank() && !line.trim().startsWith("*") && !line.trim().startsWith("/**")
                    && !line.trim().startsWith("*/")) {
                if (!PUBLIC_CLASS_PATTERN.matcher(line).find()
                        && !PUBLIC_METHOD_PATTERN.matcher(line).find()) {
                    previousLineIsJavadoc = false;
                }
            }
        }

        // Check for empty catch blocks (multi-line pattern)
        checkEmptyCatchBlocks(lines, issues);
    }

    /**
     * Checks whether the lines immediately above the given index contain a Javadoc block.
     */
    private boolean hasJavadocAbove(List<String> lines, int currentIndex) {
        // Walk backwards from the current line, skipping blank lines and annotations
        for (int i = currentIndex - 1; i >= 0; i--) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("@")) {
                // Skip annotations — Javadoc may be above them
                continue;
            }
            if (trimmed.equals("*/") || trimmed.startsWith("*") || trimmed.startsWith("/**")) {
                return true;
            }
            // Hit a non-Javadoc, non-annotation, non-blank line
            return false;
        }
        return false;
    }

    /**
     * Strips string literals and line comments from a line to avoid false positives.
     */
    private String stripStringsAndComments(String line) {
        String result = STRING_LITERAL_PATTERN.matcher(line).replaceAll("\"\"");
        result = LINE_COMMENT_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    /**
     * Detects magic numbers (numeric literals other than 0, 1, -1) in source code,
     * ignoring values inside strings and comments.
     */
    private void checkMagicNumbers(String strippedLine, int lineNum, List<String> issues) {
        // Skip import/package lines and annotations
        String trimmed = strippedLine.trim();
        if (trimmed.startsWith("import ") || trimmed.startsWith("package ")
                || trimmed.startsWith("@") || trimmed.startsWith("*")
                || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return;
        }

        // Skip constant declarations (static final)
        if (trimmed.contains("static") && trimmed.contains("final")) {
            return;
        }

        Matcher matcher = MAGIC_NUMBER_PATTERN.matcher(strippedLine);
        while (matcher.find()) {
            String number = matcher.group(1);
            // Strip type suffixes for comparison
            String cleanNumber = number.replaceAll("[fFdDlL]$", "");

            try {
                double value = Double.parseDouble(cleanNumber);
                if (value != 0.0 && value != 1.0 && value != -1.0) {
                    issues.add("[WARNING] Line %d: Magic number '%s' — extract to a named constant."
                            .formatted(lineNum, number));
                    break; // Report only the first magic number per line
                }
            } catch (NumberFormatException e) {
                // Not a valid number — skip
            }
        }
    }

    /**
     * Detects empty catch blocks by examining multi-line patterns.
     */
    private void checkEmptyCatchBlocks(List<String> lines, List<String> issues) {
        String fullText = String.join("\n", lines);
        Matcher matcher = EMPTY_CATCH_PATTERN.matcher(fullText);
        while (matcher.find()) {
            // Count newlines before the match to determine the line number
            int lineNum = 1;
            for (int i = 0; i < matcher.start(); i++) {
                if (fullText.charAt(i) == '\n') {
                    lineNum++;
                }
            }
            issues.add("[ERROR] Line %d: Empty catch block — exceptions should be logged or handled."
                    .formatted(lineNum));
        }
    }

    // ── Tier 2 Implementation ────────────────────────────────────────

    /**
     * Attempts to run {@code javac -Xlint:all} on the file for compiler-level warnings.
     * Fails gracefully if javac is unavailable.
     */
    private void runJavacLint(Path filePath, List<String> issues) {
        try {
            Path tempDir = Files.createTempDirectory("agent-lint-out");
            ProcessBuilder pb = new ProcessBuilder(
                    "javac", "-Xlint:all", "-d", tempDir.toString(), filePath.toString()
            );
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Capture stderr (where javac puts warnings)
            String stderr;
            try (var reader = process.errorReader()) {
                stderr = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();

            if (!stderr.isBlank()) {
                issues.add("");
                issues.add("── javac -Xlint:all output ──────────────────────");
                for (String line : stderr.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        issues.add("[WARNING] javac: " + trimmed);
                    }
                }
            }

            // Clean up temp directory
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }

        } catch (IOException e) {
            issues.add("");
            issues.add("[INFO] javac -Xlint check skipped: javac not available (%s)."
                    .formatted(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            issues.add("");
            issues.add("[INFO] javac -Xlint check interrupted.");
        }
    }
}
