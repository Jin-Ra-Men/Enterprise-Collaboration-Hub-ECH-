package com.ech.backend.api.aigateway;

import com.ech.backend.api.settings.AppSettingsService;
import com.ech.backend.domain.settings.AppSettingKey;
import org.springframework.stereotype.Component;

/**
 * Resolves AI gateway settings: {@code app_settings} non-blank values override {@link AiGatewayProperties}.
 */
@Component
public class AiGatewayEffectiveSettings implements AiGatewayConfigurable {

    private final AiGatewayProperties yaml;
    private final AppSettingsService appSettingsService;

    public AiGatewayEffectiveSettings(AiGatewayProperties yaml, AppSettingsService appSettingsService) {
        this.yaml = yaml;
        this.appSettingsService = appSettingsService;
    }

    @Override
    public boolean isAllowExternalLlm() {
        String def = Boolean.toString(yaml.isAllowExternalLlm());
        return parseBool(appSettingsService.get(AppSettingKey.AI_GATEWAY_ALLOW_EXTERNAL_LLM, def));
    }

    @Override
    public String getPolicyVersion() {
        String ver = yaml.getPolicyVersion();
        return appSettingsService.get(AppSettingKey.AI_GATEWAY_POLICY_VERSION, ver != null ? ver : "");
    }

    @Override
    public int getChatMaxRequestsPerMinute() {
        return parseInt(
                appSettingsService.get(
                        AppSettingKey.AI_GATEWAY_CHAT_MAX_REQUESTS_PER_MINUTE,
                        Integer.toString(yaml.getChatMaxRequestsPerMinute())),
                yaml.getChatMaxRequestsPerMinute());
    }

    @Override
    public int getChatMaxRequestsPerHour() {
        return parseInt(
                appSettingsService.get(
                        AppSettingKey.AI_GATEWAY_CHAT_MAX_REQUESTS_PER_HOUR,
                        Integer.toString(yaml.getChatMaxRequestsPerHour())),
                yaml.getChatMaxRequestsPerHour());
    }

    @Override
    public boolean isLlmHttpEnabled() {
        String def = Boolean.toString(yaml.getLlm().isHttpEnabled());
        return parseBool(appSettingsService.get(AppSettingKey.AI_LLM_HTTP_ENABLED, def));
    }

    @Override
    public String getLlmBaseUrl() {
        return appSettingsService.get(AppSettingKey.AI_LLM_BASE_URL, blankToEmpty(yaml.getLlm().getBaseUrl()));
    }

    @Override
    public String getLlmApiKey() {
        return appSettingsService.get(AppSettingKey.AI_LLM_API_KEY, blankToEmpty(yaml.getLlm().getApiKey()));
    }

    @Override
    public String getLlmModel() {
        return appSettingsService.get(AppSettingKey.AI_LLM_MODEL, yaml.getLlm().getModel());
    }

    @Override
    public int getLlmMaxTokens() {
        return parseInt(
                appSettingsService.get(AppSettingKey.AI_LLM_MAX_TOKENS, Integer.toString(yaml.getLlm().getMaxTokens())),
                yaml.getLlm().getMaxTokens());
    }

    @Override
    public boolean isLlmHttpConfigured() {
        return isLlmHttpEnabled()
                && !getLlmBaseUrl().isBlank()
                && !getLlmApiKey().isBlank();
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean parseBool(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim().toLowerCase();
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
