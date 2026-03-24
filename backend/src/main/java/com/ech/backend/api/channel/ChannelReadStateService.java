package com.ech.backend.api.channel;

import com.ech.backend.api.channel.dto.ChannelReadStateResponse;
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
import java.util.Optional;
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

    public ChannelReadStateResponse getReadState(Long channelId, Long userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            throw new IllegalArgumentException("채널 멤버만 읽음 상태를 조회할 수 있습니다.");
        }

        Optional<ChannelReadState> existing = channelReadStateRepository.findByChannel_IdAndUser_Id(
                channelId,
                userId
        );
        if (existing.isEmpty()) {
            return new ChannelReadStateResponse(channel.getId(), userId, null, null);
        }
        ChannelReadState state = existing.get();
        Long lastId = state.getLastReadMessage() == null ? null : state.getLastReadMessage().getId();
        return new ChannelReadStateResponse(channel.getId(), userId, lastId, state.getUpdatedAt());
    }

    @Transactional
    public ChannelReadStateResponse updateReadState(Long channelId, UpdateChannelReadStateRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, request.userId())) {
            throw new IllegalArgumentException("채널 멤버만 읽음 포인터를 갱신할 수 있습니다.");
        }

        Message message = messageRepository.findByIdAndChannel_Id(request.lastReadMessageId(), channelId)
                .orElseThrow(() -> new IllegalArgumentException("해당 채널에 속한 메시지를 찾을 수 없습니다."));

        ChannelReadState state = channelReadStateRepository
                .findByChannel_IdAndUser_Id(channelId, request.userId())
                .orElseGet(() -> new ChannelReadState(channel, user, message));
        if (state.getId() != null) {
            state.setLastReadMessage(message);
        }
        state = channelReadStateRepository.save(state);

        Long lastId = state.getLastReadMessage() == null ? null : state.getLastReadMessage().getId();
        return new ChannelReadStateResponse(channel.getId(), request.userId(), lastId, state.getUpdatedAt());
    }
}
