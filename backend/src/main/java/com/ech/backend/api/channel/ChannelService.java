package com.ech.backend.api.channel;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.auth.AuthService;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.common.security.UserPrincipal;
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
import com.ech.backend.domain.channel.ChannelMemberUserIdColumnInspector;
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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final AuthService authService;
    private final ChannelMemberUserIdColumnInspector channelMemberUserIdColumnInspector;
    private final JdbcTemplate jdbcTemplate;

    public ChannelService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            UserRepository userRepository,
            OrgGroupMemberRepository orgGroupMemberRepository,
            AuditLogService auditLogService,
            AuthService authService,
            ChannelMemberUserIdColumnInspector channelMemberUserIdColumnInspector,
            JdbcTemplate jdbcTemplate
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.userRepository = userRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.auditLogService = auditLogService;
        this.authService = authService;
        this.channelMemberUserIdColumnInspector = channelMemberUserIdColumnInspector;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ChannelResponse createChannel(CreateChannelRequest request, UserPrincipal principal) {
        User creator = authService.findUserForPrincipal(principal)
                .orElseThrow(() -> new IllegalArgumentException("생성자를 찾을 수 없습니다."));
        String creatorEmpNo = creator.getEmployeeNo() == null ? "" : creator.getEmployeeNo().trim();
        if (creatorEmpNo.isBlank()) {
            throw new IllegalArgumentException("사용자 사원번호가 비어 있습니다. DB users.employee_no 및 로그인 계정을 확인하세요.");
        }

        List<String> normalizedPeerNos = request.dmPeerEmployeeNos().stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .toList();

        boolean dmWithPeers = request.channelType() == ChannelType.DM && !normalizedPeerNos.isEmpty();

        if (dmWithPeers) {
            List<String> participants = new ArrayList<>();
            participants.add(creatorEmpNo);
            participants.addAll(normalizedPeerNos);
            List<String> distinctSorted = participants.stream().distinct().sorted().toList();
            for (String employeeNo : distinctSorted) {
                if (userRepository.findByEmployeeNo(employeeNo).isEmpty()) {
                    throw new IllegalArgumentException("존재하지 않는 사용자 사번입니다: " + employeeNo);
                }
            }

            String internalName = buildDmCanonicalName(distinctSorted);
            String displayLabel = (request.name() != null && !request.name().isBlank())
                    ? request.name().trim()
                    : distinctSorted.stream()
                            .filter(emp -> !emp.equals(creatorEmpNo))
                            .map(emp -> userRepository.findByEmployeeNo(emp).map(User::getName).orElse("user#" + emp))
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
            for (String emp : distinctSorted) {
                if (emp.equals(creator.getEmployeeNo())) {
                    continue;
                }
                User peer = userRepository.findByEmployeeNo(emp).orElseThrow();
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

    private static String buildDmCanonicalName(List<String> sortedParticipantIds) {
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

    private void ensureDmParticipantsMembers(Channel channel, List<String> participantIds) {
        String creatorEmployeeNo = channel.getCreatedBy() != null && channel.getCreatedBy().getEmployeeNo() != null
                ? channel.getCreatedBy().getEmployeeNo()
                : "";
        for (String employeeNo : participantIds) {
            if (channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channel.getId(), employeeNo)) {
                continue;
            }
            User u = userRepository.findByEmployeeNo(employeeNo)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + employeeNo));
            ChannelMemberRole role = employeeNo.equals(creatorEmployeeNo) ? ChannelMemberRole.MANAGER : ChannelMemberRole.MEMBER;
            channelMemberRepository.save(new ChannelMember(channel, u, role));
        }
    }

    public ChannelResponse getChannel(Long channelId) {
        Channel channel = channelRepository.findByIdWithCreatedBy(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        List<ChannelMember> members = channelMemberRepository.findByChannelIdFetchUsers(channelId);
        return toResponse(channel, members);
    }

    @Transactional
    public ChannelResponse joinChannel(Long channelId, JoinChannelRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User user = userRepository.findByEmployeeNo(request.employeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, request.employeeNo())) {
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

    public List<ChannelSummaryResponse> getMyChannels(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return List.of();
        }
        String emp = employeeNo.trim();
        List<Channel> channels;
        if (channelMemberUserIdColumnInspector.isLegacyUserIdReferencesUserPrimaryKey()) {
            List<Long> orderedIds = jdbcTemplate.query(
                    """
                            SELECT c.id FROM channels c
                            WHERE EXISTS (
                                SELECT 1 FROM channel_members cm
                                INNER JOIN users u ON u.id = cm.user_id
                                WHERE cm.channel_id = c.id AND u.employee_no = ?
                            )
                            ORDER BY c.created_at DESC
                            """,
                    (rs, rowNum) -> rs.getLong(1),
                    emp
            );
            if (orderedIds.isEmpty()) {
                return List.of();
            }
            Map<Long, Channel> byId = channelRepository.findAllById(orderedIds).stream()
                    .collect(Collectors.toMap(Channel::getId, ch -> ch, (a, b) -> a));
            channels = orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        } else {
            channels = channelRepository.findByMemberEmployeeNo(emp);
        }

        return channels.stream()
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
        List<String> employeeNos = members.stream()
                .map(m -> m.getUser().getEmployeeNo())
                .distinct()
                .toList();

        Map<String, String> departmentByEmp = membershipDisplayByEmp("TEAM", employeeNos);
        Map<String, String> levelByEmp = membershipDisplayByEmp("JOB_LEVEL", employeeNos);
        Map<String, String> positionByEmp = membershipDisplayByEmp("JOB_POSITION", employeeNos);
        Map<String, String> titleByEmp = membershipDisplayByEmp("JOB_TITLE", employeeNos);

        List<ChannelMemberResponse> memberResponses = members.stream()
                .map(member -> {
                    String emp = member.getUser().getEmployeeNo();
                    return new ChannelMemberResponse(
                            member.getUser().getEmployeeNo(),
                            member.getUser().getName(),
                            departmentByEmp.getOrDefault(emp, ""),
                            levelByEmp.getOrDefault(emp, null),
                            positionByEmp.getOrDefault(emp, null),
                            titleByEmp.getOrDefault(emp, null),
                            member.getMemberRole().name(),
                            member.getJoinedAt()
                    );
                })
                .toList();

        String createdByEmp = "";
        if (channel.getCreatedBy() != null && channel.getCreatedBy().getEmployeeNo() != null) {
            createdByEmp = channel.getCreatedBy().getEmployeeNo();
        }

        return new ChannelResponse(
                channel.getId(),
                channel.getWorkspaceKey(),
                channel.getName(),
                channel.getDescription(),
                channel.getChannelType().name(),
                createdByEmp,
                channel.getCreatedAt(),
                memberResponses
        );
    }

    private Map<String, String> membershipDisplayByEmp(String memberGroupType, List<String> employeeNos) {
        if (employeeNos.isEmpty()) {
            return Map.of();
        }
        List<OrgGroupMember> rows = orgGroupMemberRepository.findMembersByMemberGroupTypeAndEmployeeNos(
                memberGroupType,
                employeeNos
        );
        return rows.stream().collect(Collectors.toMap(
                m -> m.getUser().getEmployeeNo(),
                m -> m.getGroup().getDisplayName(),
                (a, b) -> a
        ));
    }
}
