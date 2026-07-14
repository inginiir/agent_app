package com.hrsinternational.manual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for Ollama's <strong>native</strong> {@code /api/chat} endpoint.
 *
 * <p>This client communicates with Ollama's native REST API (not the
 * OpenAI-compatible endpoint). It sends chat messages and tool schemas,
 * and parses the JSON response which includes the model's reply and any
 * tool calls.
 *
 * <p><strong>Important note on the native API:</strong> In Ollama's native
 * response format, tool call {@code arguments} are returned as a JSON
 * <em>object</em> (not a serialized string as in OpenAI's API).
 *
 * <p><strong>Debugging:</strong> Both request and response bodies are logged
 * to {@code stdout} for debugging and deliverable purposes.
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion">
 *      Ollama Chat API Documentation</a>
 */
public final class OllamaClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String baseUrl;
    private final String modelName;
    private final HttpClient httpClient;

    /**
     * Creates a new Ollama client.
     *
     * @param baseUrl   the base URL of the Ollama server (e.g., {@code http://localhost:11434})
     * @param modelName the model to use for chat completions (e.g., {@code llama3.1})
     */
    public OllamaClient(String baseUrl, String modelName) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Sends a chat request to Ollama's native {@code /api/chat} endpoint.
     *
     * <p>The request includes the conversation messages and available tool
     * schemas. The response may contain either a text reply, one or more
     * tool calls, or both.
     *
     * <p><strong>Request body format:</strong>
     * <pre>{@code
     * {
     *   "model": "llama3.1",
     *   "messages": [ ... ],
     *   "tools": [ ... ],
     *   "stream": false
     * }
     * }</pre>
     *
     * <p><strong>Response body format:</strong>
     * <pre>{@code
     * {
     *   "model": "llama3.1",
     *   "message": {
     *     "role": "assistant",
     *     "content": "...",
     *     "tool_calls": [ { "function": { "name": "...", "arguments": { ... } } } ]
     *   },
     *   "done": true
     * }
     * }</pre>
     *
     * @param messages the conversation messages (system, user, assistant, tool)
     * @param tools    the tool schemas array, or {@code null} if no tools
     * @return the full response as a {@link JsonObject}
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the request is interrupted
     */
    public JsonObject chat(JsonArray messages, JsonArray tools) throws IOException, InterruptedException {
        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.add("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            requestBody.add("tools", tools);
        }
        requestBody.addProperty("stream", false);

        String requestJson = GSON.toJson(requestBody);

        // Log request
        System.out.println("\n╔══════════════════════════════════════════════════════════════");
        System.out.println("║ OLLAMA REQUEST → POST " + baseUrl + "/api/chat");
        System.out.println("╠══════════════════════════════════════════════════════════════");
        System.out.println(requestJson);
        System.out.println("╚══════════════════════════════════════════════════════════════\n");

        // Build and send HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body();

        // Log response
        System.out.println("\n╔══════════════════════════════════════════════════════════════");
        System.out.println("║ OLLAMA RESPONSE ← " + response.statusCode());
        System.out.println("╠══════════════════════════════════════════════════════════════");
        System.out.println(responseBody);
        System.out.println("╚══════════════════════════════════════════════════════════════\n");

        if (response.statusCode() != 200) {
            throw new IOException("Ollama API returned HTTP %d: %s"
                    .formatted(response.statusCode(), responseBody));
        }

        return JsonParser.parseString(responseBody).getAsJsonObject();
    }
}
