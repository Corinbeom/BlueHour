package com.bluehour.infra.text;

import com.bluehour.domain.resume.session.port.UrlTextFetcherPort;
import com.bluehour.common.UrlFetchException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
public class JsoupUrlTextFetcherAdapter implements UrlTextFetcherPort {

    private static final int MAX_BYTES = 1_000_000; // 1MB
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public String fetch(String url) {
        if (url == null || url.isBlank()) return null;

        URI uri = parse(url);

        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            validatePublicHttpUri(uri);

            HttpResponse<byte[]> resp = send(uri);

            int status = resp.statusCode();
            if (status >= 300 && status < 400) {
                String location = resp.headers().firstValue("location").orElse(null);
                if (location == null || location.isBlank()) {
                    throw new UrlFetchException("URL 리다이렉트 location이 없습니다. status=" + status);
                }
                uri = uri.resolve(location);
                continue;
            }

            if (status < 200 || status >= 300) {
                throw new UrlFetchException("URL 응답이 실패했습니다. status=" + status);
            }

            byte[] body = resp.body() == null ? new byte[0] : resp.body();
            if (body.length > MAX_BYTES) {
                throw new UrlFetchException("URL 콘텐츠가 너무 큽니다. 최대 1MB");
            }

            String contentType = resp.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            String raw = new String(body, StandardCharsets.UTF_8);

            if (contentType.contains("text/html") || looksLikeHtml(raw)) {
                Document doc = Jsoup.parse(raw);
                return normalize(doc.text());
            }
            return normalize(raw);
        }

        throw new UrlFetchException("URL 리다이렉트가 너무 많습니다. 최대 " + MAX_REDIRECTS);
    }

    private HttpResponse<byte[]> send(URI uri) {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "DevWeb/1.0")
                .GET()
                .build();

        try {
            return httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UrlFetchException("URL 콘텐츠를 가져오지 못했습니다.", e);
        }
    }

    private static URI parse(String url) {
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new UrlFetchException("URL 형식이 올바르지 않습니다.");
        }
    }

    private static void validatePublicHttpUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UrlFetchException("http/https URL만 지원합니다.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UrlFetchException("URL host가 올바르지 않습니다.");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new UrlFetchException("URL host를 해석할 수 없습니다.");
        }

        for (InetAddress addr : addresses) {
            if (addr.isAnyLocalAddress()
                    || addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isMulticastAddress()) {
                throw new UrlFetchException("사설 네트워크/로컬 주소로의 접근은 허용되지 않습니다.");
            }
        }
    }

    private static boolean looksLikeHtml(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("<!doctype html") || s.startsWith("<html") || s.contains("<head") || s.contains("<body");
    }

    private static String normalize(String text) {
        if (text == null) return null;
        String t = text.replace('\u0000', ' ').trim();
        if (t.length() > 50_000) {
            return t.substring(0, 50_000) + "\n...(truncated)...";
        }
        return t;
    }
}
