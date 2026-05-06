package com.ech.backend.api.aigateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiGatewayService 입력 코드포인트 자르기")
class AiGatewayInputTruncateTest {

    @Test
    @DisplayName("ASCII는 코드포인트 상한에서 줄임표 포함 길이를 맞춘다")
    void ascii_truncates_with_ellipsis_within_limit() {
        assertThat(AiGatewayService.truncateToMaxCodePoints("abcdefghij", 5)).isEqualTo("abcd…");
        assertThat(AiGatewayService.exceedsCodePointLimit("abcdefghij", 5)).isTrue();
        assertThat(AiGatewayService.exceedsCodePointLimit("abcd", 5)).isFalse();
    }

    @Test
    @DisplayName("확장 영역 문자 코드포인트 단위로 자르고 서로게이트 쌍을 분할하지 않는다")
    void emoji_truncates_on_code_point_boundary() {
        String threeEmoji = "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE03";
        assertThat(threeEmoji.codePointCount(0, threeEmoji.length())).isEqualTo(3);
        String cut = AiGatewayService.truncateToMaxCodePoints(threeEmoji, 2);
        assertThat(cut).isEqualTo("\uD83D\uDE00…");
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(2);
    }
}
