package com.ech.backend.api.aigateway.llm;

import com.ech.backend.api.aigateway.AiGatewayConfigurable;
import com.ech.backend.common.exception.AiGatewayLlmUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Minimal OpenAI-compatible chat completions client ({@code POST /v1/chat/completions}).
 * Reads {@link AiGatewayConfigurable} on each call so 기초설정(app_settings) 변경이 재기동 없이 반영된다.
 */
public final class OpenAiCompatibleLlmClient implements LlmInvocationPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final RestTemplate restTemplate;
    private final AiGatewayConfigurable gatewaySettings;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(
            RestTemplate restTemplate,
            AiGatewayConfigurable gatewaySettings,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.gatewaySettings = gatewaySettings;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return gatewaySettings.isLlmHttpConfigured();
    }

    @Override
    public Optional<LlmCompletionResult> complete(String maskedUserPrompt, String purpose) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String base = gatewaySettings.getLlmBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri = URI.create(base + "/v1/chat/completions");

        String model = gatewaySettings.getLlmModel();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", maskedUserPrompt == null ? "" : maskedUserPrompt);
        root.put("max_tokens", gatewaySettings.getLlmMaxTokens());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(gatewaySettings.getLlmApiKey());

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);
        } catch (Exception ex) {
            throw new AiGatewayLlmUpstreamException("LLM 요청 본문 생성에 실패했습니다.", ex);
        }

        try {
            ResponseEntity<JsonNode> resp =
                    restTemplate.exchange(uri, HttpMethod.POST, entity, JsonNode.class);
            JsonNode body = resp.getBody();
            if (body == null) {
                throw new AiGatewayLlmUpstreamException("LLM 응답 본문이 비어 있습니다.");
            }
            JsonNode choices = body.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("OpenAI-compatible response had no choices (purpose={})", purpose);
                return Optional.empty();
            }
            String text = choices.get(0).path("message").path("content").asText("");
            String resolvedModel = body.path("model").asText(model);
            Integer tokens = null;
            if (body.path("usage").path("total_tokens").canConvertToInt()) {
                tokens = body.path("usage").path("total_tokens").asInt();
            }
            return Optional.of(new LlmCompletionResult(text, resolvedModel, tokens));
        } catch (RestClientResponseException ex) {
            log.warn("LLM HTTP error status={} purpose={}", ex.getStatusCode().value(), purpose);
            throw new AiGatewayLlmUpstreamException(
                    "외부 LLM 서비스가 오류 응답을 반환했습니다. 설정·쿼터·네트워크를 확인해 주세요.",
                    ex);
        } catch (AiGatewayLlmUpstreamException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiGatewayLlmUpstreamException("외부 LLM 호출 중 오류가 발생했습니다.", ex);
        }
    }
}
