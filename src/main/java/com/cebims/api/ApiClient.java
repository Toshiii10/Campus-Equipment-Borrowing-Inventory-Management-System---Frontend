package com.cebims.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Talks to the CEBIMS backend. Adds the Bearer token automatically and, when the access token 
 * has expired (401), swaps the refresh token for a new pair and retries once.
 * All calls are async: never block the JavaFX thread, and update the UI inside Platform.runLater.
 */
public final class ApiClient {
    public static final String BASE_URL = "http://localhost:8080";
    public static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static CompletableFuture<Boolean> refreshing; // shared so parallel 401s refresh only once

    private ApiClient() {}

    public record Response(int status, String body) {
        public boolean ok() { return status >= 200 && status < 300; }

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Backend errors look like {"error": "...", "fields": {"name": "..."}}. */
        public String errorMessage() {
            try {
                JsonNode n = JSON.readTree(body);
                StringBuilder sb = new StringBuilder(n.path("error").asText("Request failed (HTTP " + status + ")"));
                JsonNode f = n.path("fields");
                if (f.isObject()) {
                    f.fieldNames().forEachRemaining(k ->
                            sb.append("\n- ").append(k).append(": ").append(f.get(k).asText()));
                }
                return sb.toString();
            } catch (Exception e) {
                return "Request failed (HTTP " + status + ")";
            }
        }
    }

    /** A file/image to upload in a multipart request. */
    public record FilePart(String fieldName, String fileName, String contentType, byte[] data) {
        public static FilePart fromFile(String fieldName, Path file) throws IOException {
            String name = file.getFileName().toString();
            String lower = name.toLowerCase();
            String type = lower.endsWith(".png") ? "image/png"
                    : lower.endsWith(".webp") ? "image/webp"
                    : lower.endsWith(".gif") ? "image/gif"
                    : "image/jpeg";
            return new FilePart(fieldName, name.replaceAll("[^A-Za-z0-9._-]", "_"), type, Files.readAllBytes(file));
        }
    }

    public static String absolute(String pathOrUrl) {
        return pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") ? pathOrUrl : BASE_URL + pathOrUrl;
    }

    // ---- JSON requests ---------------------------------------------------------------------

    public static CompletableFuture<Response> get(String path)               { return send("GET", path, null); }
    public static CompletableFuture<Response> post(String path, Object body) { return send("POST", path, body); }
    public static CompletableFuture<Response> put(String path, Object body)  { return send("PUT", path, body); }
    public static CompletableFuture<Response> delete(String path)            { return send("DELETE", path, null); }
    public static CompletableFuture<Response> patch(String path, Object body) { return send("PATCH", path, body); }

    private static CompletableFuture<Response> send(String method, String path, Object body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            return request(method, path, publisher, "application/json");
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ---- multipart upload (photos, request verification proof) -----------------------------

    public static CompletableFuture<Response> postMultipart(String path, Map<String, String> fields, FilePart file) {
        try {
            String boundary = "----CEBIMS" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipartBody(boundary, fields, file);
            return request("POST", path, HttpRequest.BodyPublishers.ofByteArray(body),
                    "multipart/form-data; boundary=" + boundary);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static byte[] multipartBody(String boundary, Map<String, String> fields, FilePart file) throws IOException {
        final String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            out.write(("--" + boundary + crlf
                    + "Content-Disposition: form-data; name=\"" + e.getKey() + "\"" + crlf + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
        }
        if (file != null) {
            out.write(("--" + boundary + crlf
                    + "Content-Disposition: form-data; name=\"" + file.fieldName() + "\"; filename=\""
                    + file.fileName() + "\"" + crlf
                    + "Content-Type: " + file.contentType() + crlf + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(file.data());
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    // ---- authenticated image download ------------------------------------------------------

    /** Downloads raw bytes (photos). Needed because private photos require the Bearer header. */
    public static CompletableFuture<byte[]> getBytes(String pathOrUrl) {
        final String used = Session.accessToken();
        return rawBytes(pathOrUrl, used).thenCompose(r -> {
            if (r.statusCode() == 401 && Session.refreshToken() != null) {
                String current = Session.accessToken();
                if (current != null && !current.equals(used)) return rawBytes(pathOrUrl, current);
                return refreshTokens().thenCompose(ok -> ok
                        ? rawBytes(pathOrUrl, Session.accessToken())
                        : CompletableFuture.completedFuture(r));
            }
            return CompletableFuture.completedFuture(r);
        }).thenApply(r -> {
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                throw new CompletionException(new IOException("HTTP " + r.statusCode()));
            }
            return r.body();
        });
    }

    private static CompletableFuture<HttpResponse<byte[]>> rawBytes(String pathOrUrl, String token) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(absolute(pathOrUrl))).GET();
            if (token != null) req.header("Authorization", "Bearer " + token);
            return HTTP.sendAsync(req.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ---- core: send, and retry once after a token refresh ----------------------------------

    private static CompletableFuture<Response> request(String method, String path,
                                                       HttpRequest.BodyPublisher body, String contentType) {
        final String usedToken = Session.accessToken();
        return raw(method, path, body, contentType, usedToken).thenCompose(r -> {
            boolean canRetry = r.status() == 401
                    && Session.refreshToken() != null
                    && !path.startsWith("/v1/auth/");
            if (!canRetry) return CompletableFuture.completedFuture(r);

            // Another request already refreshed while this one was in flight: just retry.
            String current = Session.accessToken();
            if (current != null && !current.equals(usedToken)) {
                return raw(method, path, body, contentType, current);
            }
            return refreshTokens().thenCompose(ok -> ok
                    ? raw(method, path, body, contentType, Session.accessToken())
                    : CompletableFuture.completedFuture(r));
        });
    }

    private static CompletableFuture<Response> raw(String method, String path, HttpRequest.BodyPublisher body,
                                                   String contentType, String token) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(absolute(path)))
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json")
                    .method(method, body);
            if (token != null) req.header("Authorization", "Bearer " + token);
            return HTTP.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> new Response(r.statusCode(), r.body()));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Refresh tokens work once, so only one refresh may run at a time. */
    private static synchronized CompletableFuture<Boolean> refreshTokens() {
        if (refreshing != null) return refreshing;
        CompletableFuture<Response> call;
        try {
            call = raw("POST", "/v1/auth/refresh",
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                            Map.of("refreshToken", Session.refreshToken()))),
                    "application/json", null);
        } catch (IOException e) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> f = call
                .thenApply(r -> {
                    if (r.ok()) {
                        JsonNode n = r.json();
                        Session.setTokens(n.path("accessToken").asText(), n.path("refreshToken").asText());
                        return true;
                    }
                    Session.fireLoggedOut();
                    return false;
                })
                .exceptionally(e -> false);
        refreshing = f;
        f.whenComplete((v, e) -> { synchronized (ApiClient.class) { refreshing = null; } });
        return f;
    }
}