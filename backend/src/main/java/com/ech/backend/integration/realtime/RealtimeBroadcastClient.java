package com.ech.backend.integration.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Node 실시간 서버로 채널 단위 이벤트를 HTTP로 알린다(내부 전용).
 * 연결 실패는 로그만 남기고 호출자 트랜잭션에는 영향을 주지 않는다.
 */
@Component
public class RealtimeBroadcastClient {

    private static final Logger log = LoggerFactory.getLogger(RealtimeBroadcastClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.realtime.internal-base-url:http://localhost:3001}")
    private String internalBaseUrl;

    @Value("${app.realtime.internal-token:}")
    private String internalToken;

    public void broadcastChannelSystem(long channelId, String text, String createdAtIso, Long messageId) {
        String base = internalBaseUrl == null ? "" : internalBaseUrl.trim();
        if (base.isEmpty()) {
            return;
        }
        String url = base.endsWith("/") ? base + "internal/broadcast-channel-system" : base + "/internal/broadcast-channel-system";
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("channelId", channelId);
            body.put("text", text);
            body.put("createdAt", createdAtIso);
            if (messageId != null) {
                body.put("messageId", messageId);
            }
            byte[] json = objectMapper.writeValueAsBytes(body);

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json));
            if (internalToken != null && !internalToken.isBlank()) {
                b.header("X-Internal-Token", internalToken.trim());
            }

            HttpResponse<String> res = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                log.warn("realtime broadcast HTTP {}: {}", res.statusCode(), res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("realtime broadcast interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("realtime broadcast failed: {}", e.getMessage());
        }
    }

    /** 멘션 알림을 Node가 대상 사원 소켓에 {@code mention:notify}로 전달한다. */
    public void notifyMentions(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String base = internalBaseUrl == null ? "" : internalBaseUrl.trim();
        if (base.isEmpty()) {
            return;
        }
        String url = base.endsWith("/") ? base + "internal/notify-mentions" : base + "/internal/notify-mentions";
        try {
            Map<String, Object> body = Map.of("items", items);
            byte[] json = objectMapper.writeValueAsBytes(body);

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json));
            if (internalToken != null && !internalToken.isBlank()) {
                b.header("X-Internal-Token", internalToken.trim());
            }

            HttpResponse<String> res = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                log.warn("realtime notify-mentions HTTP {}: {}", res.statusCode(), res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("realtime notify-mentions interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("realtime notify-mentions failed: {}", e.getMessage());
        }
    }

    /** 일정 공유요청 알림을 대상 사원 소켓에 {@code calendar:share-request}로 전달한다. */
    public void notifyCalendarShareRequests(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String base = internalBaseUrl == null ? "" : internalBaseUrl.trim();
        if (base.isEmpty()) {
            return;
        }
        String url = base.endsWith("/")
                ? base + "internal/notify-calendar-shares"
                : base + "/internal/notify-calendar-shares";
        try {
            Map<String, Object> body = Map.of("items", items);
            byte[] json = objectMapper.writeValueAsBytes(body);

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json));
            if (internalToken != null && !internalToken.isBlank()) {
                b.header("X-Internal-Token", internalToken.trim());
            }

            HttpResponse<String> res = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                log.warn("realtime notify-calendar-shares HTTP {}: {}", res.statusCode(), res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("realtime notify-calendar-shares interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("realtime notify-calendar-shares failed: {}", e.getMessage());
        }
    }
}
