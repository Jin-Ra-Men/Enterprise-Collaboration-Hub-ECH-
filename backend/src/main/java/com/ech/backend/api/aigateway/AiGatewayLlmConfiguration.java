package com.ech.backend.api.aigateway;

import com.ech.backend.api.aigateway.llm.LlmInvocationPort;
import com.ech.backend.api.aigateway.llm.NoopLlmInvocationClient;
import com.ech.backend.api.aigateway.llm.OpenAiCompatibleLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiGatewayLlmConfiguration {

    @Bean
    public LlmInvocationPort llmInvocationPort(
            RestTemplateBuilder restTemplateBuilder,
            AiGatewayProperties properties,
            ObjectMapper objectMapper
    ) {
        AiGatewayProperties.LlmHttp llm = properties.getLlm();
        RestTemplate rt = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
        if (llm.isHttpEnabled()
                && llm.getBaseUrl() != null
                && !llm.getBaseUrl().isBlank()
                && llm.getApiKey() != null
                && !llm.getApiKey().isBlank()) {
            return new OpenAiCompatibleLlmClient(rt, properties, objectMapper);
        }
        return new NoopLlmInvocationClient();
    }
}
