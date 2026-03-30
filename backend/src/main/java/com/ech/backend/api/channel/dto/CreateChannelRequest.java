package com.ech.backend.api.channel.dto;

import com.ech.backend.domain.channel.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 채널 생성 요청. 실제 생성자 식별은 JWT(로그인 계정의 사원번호)로만 결정한다.
 * {@code createdByEmployeeNo}는 구 클라이언트 호환용 선택 필드이며, 서버는 이를 생성자 결정에 사용하지 않는다.
 */
public record CreateChannelRequest(
        @NotBlank @Size(max = 100) String workspaceKey,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull ChannelType channelType,
        @Size(max = 50) String createdByEmployeeNo,
        List<String> dmPeerEmployeeNos
) {
    public CreateChannelRequest {
        dmPeerEmployeeNos = dmPeerEmployeeNos == null ? List.of() : List.copyOf(dmPeerEmployeeNos);
    }
}
