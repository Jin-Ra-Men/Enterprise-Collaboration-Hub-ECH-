package com.ech.backend.api.channel;

import com.ech.backend.api.channel.dto.ChannelMemberResponse;
import com.ech.backend.api.channel.dto.ChannelResponse;
import com.ech.backend.api.channel.dto.CreateChannelRequest;
import com.ech.backend.api.channel.dto.JoinChannelRequest;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRole;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;

    public ChannelService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChannelResponse createChannel(CreateChannelRequest request) {
        if (channelRepository.findByWorkspaceKeyAndName(request.workspaceKey(), request.name()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 채널 이름입니다.");
        }

        User creator = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new IllegalArgumentException("생성자를 찾을 수 없습니다."));

        Channel channel = new Channel(
                request.workspaceKey(),
                request.name(),
                request.description(),
                request.channelType(),
                creator
        );
        Channel savedChannel = channelRepository.save(channel);

        ChannelMember ownerMembership = new ChannelMember(savedChannel, creator, ChannelMemberRole.MANAGER);
        channelMemberRepository.save(ownerMembership);

        return toResponse(savedChannel, List.of(ownerMembership));
    }

    public ChannelResponse getChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        return toResponse(channel, members);
    }

    @Transactional
    public ChannelResponse joinChannel(Long channelId, JoinChannelRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, request.userId())) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }

        channelMemberRepository.save(new ChannelMember(channel, user, request.memberRole()));
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        return toResponse(channel, members);
    }

    private ChannelResponse toResponse(Channel channel, List<ChannelMember> members) {
        List<ChannelMemberResponse> memberResponses = members.stream()
                .map(member -> new ChannelMemberResponse(
                        member.getUser().getId(),
                        member.getMemberRole().name(),
                        member.getJoinedAt()
                ))
                .toList();

        return new ChannelResponse(
                channel.getId(),
                channel.getWorkspaceKey(),
                channel.getName(),
                channel.getDescription(),
                channel.getChannelType().name(),
                channel.getCreatedBy().getId(),
                channel.getCreatedAt(),
                memberResponses
        );
    }
}
