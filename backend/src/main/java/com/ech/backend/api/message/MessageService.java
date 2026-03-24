package com.ech.backend.api.message;

import com.ech.backend.api.message.dto.CreateMessageRequest;
import com.ech.backend.api.message.dto.MessageResponse;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public MessageService(
            ChannelRepository channelRepository,
            UserRepository userRepository,
            MessageRepository messageRepository
    ) {
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public MessageResponse createMessage(Long channelId, CreateMessageRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User sender = userRepository.findById(request.senderId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Message message = messageRepository.save(new Message(channel, sender, null, request.text()));
        return toResponse(message);
    }

    @Transactional
    public MessageResponse createReply(Long channelId, Long parentMessageId, CreateMessageRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User sender = userRepository.findById(request.senderId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Message parent = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("부모 메시지를 찾을 수 없습니다."));

        if (!parent.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("부모 메시지의 채널이 일치하지 않습니다.");
        }

        Message reply = messageRepository.save(new Message(channel, sender, parent, request.text()));
        return toResponse(reply);
    }

    public List<MessageResponse> getThreadReplies(Long channelId, Long parentMessageId) {
        Message parent = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("부모 메시지를 찾을 수 없습니다."));
        if (!parent.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("부모 메시지의 채널이 일치하지 않습니다.");
        }

        return messageRepository.findByParentMessageIdOrderByCreatedAtAsc(parentMessageId).stream()
                .map(this::toResponse)
                .toList();
    }

    private MessageResponse toResponse(Message message) {
        Long parentMessageId = message.getParentMessage() == null ? null : message.getParentMessage().getId();
        return new MessageResponse(
                message.getId(),
                message.getChannel().getId(),
                message.getSender().getId(),
                parentMessageId,
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
