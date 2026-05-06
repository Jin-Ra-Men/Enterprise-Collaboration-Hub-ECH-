package com.ech.backend.api.aigateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiGatewayPiiMaskerTest {

    @Test
    @DisplayName("주민번호 유사 형태를 치환한다")
    void masks_rrn_like_pattern() {
        String raw = "x 900101-1234567 y";
        AiGatewayPiiMasker.MaskResult r = AiGatewayPiiMasker.mask(raw);
        assertThat(r.redactionCount()).isEqualTo(1);
        assertThat(r.maskedText()).doesNotContain("900101-1234567").contains("[REDACTED_ID]");
    }

    @Test
    @DisplayName("공백·하이픈 구분 16자리 카드 형태를 치환한다")
    void masks_spaced_or_hyphen_card_pattern() {
        AiGatewayPiiMasker.MaskResult spaced = AiGatewayPiiMasker.mask("pay 4111 1111 1111 1111 done");
        assertThat(spaced.redactionCount()).isEqualTo(1);
        assertThat(spaced.maskedText()).doesNotContain("4111").contains("[REDACTED_PAYMENT]");

        AiGatewayPiiMasker.MaskResult hyphen = AiGatewayPiiMasker.mask("pay 4222-2222-2222-2222 done");
        assertThat(hyphen.redactionCount()).isEqualTo(1);
        assertThat(hyphen.maskedText()).contains("[REDACTED_PAYMENT]");
    }
}
