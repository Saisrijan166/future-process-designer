package com.assesswise.processdesigner.service.research;

import com.assesswise.processdesigner.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one HTTP client the research layer uses to talk to the public internet.
 *
 * <p>Everything unpleasant about fetching arbitrary URLs is handled here rather than in each of the
 * eleven connectors:
 *
 * <ul>
 *   <li><b>A hard byte ceiling.</b> Responses are read through a counting stream and cut off, so a
 *       200MB video served at a URL that promised to be an article cannot exhaust the heap of a
 *       512MB free-tier container.
 *   <li><b>Manual gzip handling.</b> {@code HttpClient} does not decompress; the Stack Exchange API
 *       compresses unconditionally. Both the header and the magic bytes are checked, because some
 *       hosts compress without saying so.
 *   <li><b>Charset from the response, not from hope.</b> Plenty of pages are still not UTF-8.
 *   <li><b>Never throwing for a bad status.</b> A 403 from a publisher that blocks robots is an
 *       expected outcome that the caller records and works around, not an exception.
 *   <li><b>The final URL after redirects.</b> Google News hands out redirect links; the real
 *       publisher is only knowable after following them, and the domain is what credibility and
 *       corroboration are computed from.
 * </ul>
 */
@Component
public class HttpResearchClient {

    private static final Logger log = LoggerFactory.getLogger(HttpResearchClient.class);

    /** Generous enough for a long standards page, small enough that ten of them are harmless. */
    private static final int DEFAULT_MAX_BYTES = 3 * 1024 * 1024;

    private final HttpClient httpClient;
    private final String userAgent;
    private final Duration timeout;

    public HttpResearchClient(AppProperties properties) {
        AppProperties.Research research = properties.research();
        this.userAgent = research.userAgent();
        this.timeout = Duration.ofSeconds(Math.max(3, research.fetchTimeoutSeconds()));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * One HTTP exchange.
     *
     * @param finalUrl where the request ended up after redirects, which is not always where it
     *     started
     * @param failure set when nothing came back at all; {@code status} is then 0
     */
    public record Response(
            int status, String body, String finalUrl, String contentType, boolean truncated, String failure) {

        public boolean isSuccess() {
            return status >= 200 && status < 300 && body != null && !body.isBlank();
        }

        public boolean looksLikeHtml() {
            return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("html");
        }

        public boolean isPdf() {
            return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf");
        }
    }

    public Response get(String url) {
        return get(url, Map.of(), DEFAULT_MAX_BYTES);
    }

    public Response get(String url, Map<String, String> extraHeaders, int maxBytes) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            return new Response(0, null, url, null, false, "Not a usable URL: " + e.getMessage());
        }
        builder.timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "gzip")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.5");
        extraHeaders.forEach(builder::header);

        return exchange(builder.GET().build(), url, maxBytes);
    }

    public Response postJson(String url, String jsonBody, Map<String, String> extraHeaders) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip");
        extraHeaders.forEach(builder::header);
        return exchange(builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(), url, DEFAULT_MAX_BYTES);
    }

    private Response exchange(HttpRequest request, String requestedUrl, int maxBytes) {
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            String contentType = response.headers().firstValue("content-type").orElse("");
            String finalUrl = response.uri() == null ? requestedUrl : response.uri().toString();

            // A PDF or a video is not worth reading into memory to discover it is not text.
            if (isBinaryNonText(contentType)) {
                response.body().close();
                return new Response(response.statusCode(), null, finalUrl, contentType, false,
                        "Content type " + contentType + " is not readable as text");
            }

            boolean gzipped = response.headers().firstValue("content-encoding")
                    .map(value -> value.toLowerCase(Locale.ROOT).contains("gzip"))
                    .orElse(false);

            ReadResult read = readCapped(response.body(), maxBytes, gzipped);
            String body = new String(read.bytes(), charsetOf(contentType));
            return new Response(response.statusCode(), body, finalUrl, contentType, read.truncated(), null);

        } catch (IOException e) {
            log.debug("Research fetch failed for {}: {}", requestedUrl, e.getMessage());
            return new Response(0, null, requestedUrl, null, false, describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(0, null, requestedUrl, null, false, "Interrupted");
        }
    }

    private record ReadResult(byte[] bytes, boolean truncated) {}

    /**
     * Reads at most {@code maxBytes}, decompressing if needed. The gzip check looks at the first
     * two bytes as well as the header, because a host that compresses without declaring it would
     * otherwise produce a page of binary noise that the extractor would faithfully try to read.
     */
    private ReadResult readCapped(InputStream source, int maxBytes, boolean declaredGzip) throws IOException {
        try (InputStream raw = source) {
            java.io.PushbackInputStream peekable = new java.io.PushbackInputStream(raw, 2);
            byte[] signature = new byte[2];
            int read = peekable.read(signature, 0, 2);
            if (read > 0) {
                peekable.unread(signature, 0, read);
            }
            boolean gzip = declaredGzip
                    || (read == 2 && (signature[0] & 0xff) == 0x1f && (signature[1] & 0xff) == 0x8b);

            try (InputStream stream = gzip ? new GZIPInputStream(peekable) : peekable) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
                byte[] chunk = new byte[16 * 1024];
                int total = 0;
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    int allowed = Math.min(count, maxBytes - total);
                    buffer.write(chunk, 0, allowed);
                    total += allowed;
                    if (total >= maxBytes) {
                        return new ReadResult(buffer.toByteArray(), true);
                    }
                }
                return new ReadResult(buffer.toByteArray(), false);
            }
        }
    }

    private static boolean isBinaryNonText(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("text/") || lower.contains("json") || lower.contains("xml") || lower.contains("html")) {
            return false;
        }
        return lower.startsWith("image/")
                || lower.startsWith("video/")
                || lower.startsWith("audio/")
                || lower.contains("pdf")
                || lower.contains("octet-stream")
                || lower.contains("zip");
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            int index = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (index >= 0) {
                String name = contentType.substring(index + 8).trim().replace("\"", "");
                int semicolon = name.indexOf(';');
                if (semicolon > 0) {
                    name = name.substring(0, semicolon);
                }
                try {
                    return Charset.forName(name.trim());
                } catch (Exception ignored) {
                    // fall through to UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String describe(IOException e) {
        String message = e.getMessage();
        String type = e.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    public static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
