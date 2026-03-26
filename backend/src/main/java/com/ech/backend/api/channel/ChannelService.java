package com.ech.backend.api.channel;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.api.channel.dto.ChannelMemberResponse;
import com.ech.backend.api.channel.dto.ChannelResponse;
import com.ech.backend.api.channel.dto.ChannelSummaryResponse;
import com.ech.backend.api.channel.dto.CreateChannelRequest;
import com.ech.backend.api.channel.dto.JoinChannelRequest;
import com.ech.backend.domain.audit.AuditEventType;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRole;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;
    private final AuditLogService auditLogService;

    public ChannelService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            OrgGroupMemberRepository orgGroupMemberRepository,
            AuditLogService auditLogService
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ChannelResponse createChannel(CreateChannelRequest request) {
        User creator = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new IllegalArgumentException("생성자를 찾을 수 없습니다."));

        boolean dmWithPeers = request.channelType() == ChannelType.DM && !request.dmPeerUserIds().isEmpty();

        if (dmWithPeers) {
            List<Long> participants = new ArrayList<>();
            participants.add(request.createdByUserId());
            participants.addAll(request.dmPeerUserIds());
            List<Long> distinctSorted = participants.stream().distinct().sorted().toList();
            for (Long uid : distinctSorted) {
                if (!userRepository.existsById(uid)) {
                    throw new IllegalArgumentException("존재하지 않는 사용자 ID입니다: " + uid);
                }
            }

            String internalName = buildDmCanonicalName(distinctSorted);
            String displayLabel = (request.name() != null && !request.name().isBlank())
                    ? request.name().trim()
                    : distinctSorted.stream()
                            .filter(id -> !id.equals(request.createdByUserId()))
                            .map(id -> userRepository.findById(id).map(User::getName).orElse("user#" + id))
                            .collect(Collectors.joining(", "));
            if (displayLabel.isBlank()) {
                displayLabel = "DM";
            }
            if (displayLabel.length() > 2000) {
                displayLabel = displayLabel.substring(0, 2000);
            }

            Optional<Channel> existing = channelRepository.findByWorkspaceKeyAndName(
                    request.workspaceKey(), internalName);
            if (existing.isPresent()) {
                Channel ch = existing.get();
                ensureDmParticipantsMembers(ch, distinctSorted);
                List<ChannelMember> members = channelMemberRepository.findByChannelId(ch.getId());
                return toResponse(ch, members);
            }

            Channel channel = new Channel(
                    request.workspaceKey(),
                    internalName,
                    displayLabel,
                    ChannelType.DM,
                    creator
            );
            Channel savedChannel = channelRepository.save(channel);
            channelMemberRepository.save(new ChannelMember(savedChannel, creator, ChannelMemberRole.MANAGER));
            for (Long uid : distinctSorted) {
                if (uid.equals(creator.getId())) {
                    continue;
                }
                User peer = userRepository.findById(uid).orElseThrow();
                channelMemberRepository.save(new ChannelMember(savedChannel, peer, ChannelMemberRole.MEMBER));
            }
            List<ChannelMember> members = channelMemberRepository.findByChannelId(savedChannel.getId());

            auditLogService.safeRecord(
                    AuditEventType.CHANNEL_CREATED,
                    creator.getId(),
                    "CHANNEL",
                    savedChannel.getId(),
                    savedChannel.getWorkspaceKey(),
                    "dm internalName=" + internalName + " label=" + displayLabel,
                    null
            );

            return toResponse(savedChannel, members);
        }

        if (channelRepository.findByWorkspaceKeyAndName(request.workspaceKey(), request.name()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 채널 이름입니다.");
        }

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

        auditLogService.safeRecord(
                AuditEventType.CHANNEL_CREATED,
                creator.getId(),
                "CHANNEL",
                savedChannel.getId(),
                savedChannel.getWorkspaceKey(),
                "name=" + savedChannel.getName(),
                null
        );

        return toResponse(savedChannel, List.of(ownerMembership));
    }

    private static String buildDmCanonicalName(List<Long> sortedParticipantIds) {
        String raw = sortedParticipantIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("_"));
        String candidate = "__dm__" + raw;
        if (candidate.length() <= 100) {
            return candidate;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            String h = HexFormat.of().formatHex(digest);
            return "__dm__h__" + h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void ensureDmParticipantsMembers(Channel channel, List<Long> participantIds) {
        Long creatorId = channel.getCreatedBy().getId();
        for (Long uid : participantIds) {
            if (channelMemberRepository.existsByChannelIdAndUserId(channel.getId(), uid)) {
                continue;
            }
            User u = userRepository.findById(uid)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + uid));
            ChannelMemberRole role = uid.equals(creatorId) ? ChannelMemberRole.MANAGER : ChannelMemberRole.MEMBER;
            channelMemberRepository.save(new ChannelMember(channel, u, role));
        }
    }

    public ChannelResponse getChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
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

        auditLogService.safeRecord(
                AuditEventType.CHANNEL_JOINED,
                user.getId(),
                "CHANNEL",
                channelId,
                channel.getWorkspaceKey(),
                "channelName=" + channel.getName(),
                null
        );

        return toResponse(channel, members);
    }

    public List<ChannelSummaryResponse> getMyChannels(Long userId) {
        return channelRepository.findByMemberId(userId).stream()
                .map(channel -> {
                    int memberCount = channelMemberRepository.findByChannelId(channel.getId()).size();
                    return new ChannelSummaryResponse(
                            channel.getId(),
                            channel.getWorkspaceKey(),
                            channel.getName(),
                            channel.getDescription(),
                            channel.getChannelType().name(),
                            memberCount,
                            channel.getCreatedAt()
                    );
                })
                .toList();
    }

    private ChannelResponse toResponse(Channel channel, List<ChannelMember> members) {
        List<Long> userIds = members.stream()
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();

        List<OrgGroupMember> teamMembers = userIds.isEmpty()
                ? List.of()
                : orgGroupMemberRepository.findMembersByMemberGroupTypeAndUserIds("TEAM", userIds);

        Map<Long, String> departmentByUserId = teamMembers.stream()
                .collect(Collectors.toMap(
                        m -> m.getUser().getId(),
                        m -> m.getGroup().getDisplayName(),
                        (a, b) -> a
                ));

        List<ChannelMemberResponse> memberResponses = members.stream()
                .map(member -> new ChannelMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getName(),
                        departmentByUserId.getOrDefault(member.getUser().getId(), member.getUser().getDepartment()),
                        member.getUser().getJobRank(),
                        member.getUser().getDutyTitle(),
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
