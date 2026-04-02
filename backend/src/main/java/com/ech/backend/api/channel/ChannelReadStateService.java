package com.ech.backend.api.channel;

import com.ech.backend.api.channel.dto.ChannelReadStateResponse;
import com.ech.backend.api.channel.dto.MarkChannelReadCaughtUpRequest;
import com.ech.backend.api.channel.dto.UpdateChannelReadStateRequest;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelReadState;
import com.ech.backend.domain.channel.ChannelReadStateRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChannelReadStateService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ChannelReadStateRepository channelReadStateRepository;

    public ChannelReadStateService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            MessageRepository messageRepository,
            ChannelReadStateRepository channelReadStateRepository
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.channelReadStateRepository = channelReadStateRepository;
    }

    public ChannelReadStateResponse getReadState(Long channelId, String employeeNo) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        userRepository.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, employeeNo)) {
            throw new IllegalArgumentException("채널 멤버만 읽음 상태를 조회할 수 있습니다.");
        }

        Optional<ChannelReadState> existing = channelReadStateRepository.findByChannel_IdAndUser_EmployeeNo(
                channelId,
                employeeNo
        );
        if (existing.isEmpty()) {
            return new ChannelReadStateResponse(channel.getId(), employeeNo, null, null);
        }
        ChannelReadState state = existing.get();
        Long lastId = state.getLastReadMessage() == null ? null : state.getLastReadMessage().getId();
        return new ChannelReadStateResponse(channel.getId(), employeeNo, lastId, state.getUpdatedAt());
    }

    @Transactional
    public ChannelReadStateResponse updateReadState(Long channelId, UpdateChannelReadStateRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User user = userRepository.findByEmployeeNo(request.employeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, request.employeeNo())) {
            throw new IllegalArgumentException("채널 멤버만 읽음 포인터를 갱신할 수 있습니다.");
        }

        Message message = messageRepository.findByIdAndChannel_Id(request.lastReadMessageId(), channelId)
                .orElseThrow(() -> new IllegalArgumentException("해당 채널에 속한 메시지를 찾을 수 없습니다."));

        ChannelReadState state = channelReadStateRepository
                .findByChannel_IdAndUser_EmployeeNo(channelId, request.employeeNo())
                .orElseGet(() -> new ChannelReadState(channel, user, message));
        if (state.getId() != null) {
            state.setLastReadMessage(message);
        }
        state = channelReadStateRepository.save(state);

        Long lastId = state.getLastReadMessage() == null ? null : state.getLastReadMessage().getId();
        return new ChannelReadStateResponse(channel.getId(), request.employeeNo(), lastId, state.getUpdatedAt());
    }

    /**
     * 채널의 최신 루트 메시지(타임라인 기준)까지 읽음 처리. 대량 히스토리에서도 첫 화면만으로 미읽음 배지를
     * 없앨 수 있게 한다.
     */
    @Transactional
    public ChannelReadStateResponse markCaughtUpToLatestRoot(Long channelId, MarkChannelReadCaughtUpRequest request) {
        channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        userRepository.findByEmployeeNo(request.employeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, request.employeeNo())) {
            throw new IllegalArgumentException("채널 멤버만 읽음 포인터를 갱신할 수 있습니다.");
        }

        List<Message> latest = messageRepository.findRecentByChannelId(channelId, PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            return getReadState(channelId, request.employeeNo());
        }
        return updateReadState(
                channelId,
                new UpdateChannelReadStateRequest(request.employeeNo(), latest.get(0).getId()));
    }
}
