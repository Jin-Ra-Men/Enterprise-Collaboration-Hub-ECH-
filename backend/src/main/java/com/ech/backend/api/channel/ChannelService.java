package com.ech.backend.api.channel;

import com.ech.backend.api.auditlog.AuditLogService;
import com.ech.backend.api.auth.AuthService;
import com.ech.backend.common.exception.ForbiddenException;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.common.exception.UnauthorizedException;
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
import com.ech.backend.domain.channel.ChannelReadStateRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.org.OrgGroupMember;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.ech.backend.integration.realtime.RealtimeBroadcastClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelReadStateRepository channelReadStateRepository;
    private final UserRepository userRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;
    private final AuditLogService auditLogService;
    private final AuthService authService;
    private final ChannelMemberUserIdColumnInspector channelMemberUserIdColumnInspector;
    private final JdbcTemplate jdbcTemplate;
    private final MessageRepository messageRepository;
    private final RealtimeBroadcastClient realtimeBroadcastClient;

    public ChannelService(
            ChannelRepository channelRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelReadStateRepository channelReadStateRepository,
            UserRepository userRepository,
            OrgGroupMemberRepository orgGroupMemberRepository,
            AuditLogService auditLogService,
            AuthService authService,
            ChannelMemberUserIdColumnInspector channelMemberUserIdColumnInspector,
            JdbcTemplate jdbcTemplate,
            MessageRepository messageRepository,
            RealtimeBroadcastClient realtimeBroadcastClient
    ) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.channelReadStateRepository = channelReadStateRepository;
        this.userRepository = userRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
        this.auditLogService = auditLogService;
        this.authService = authService;
        this.channelMemberUserIdColumnInspector = channelMemberUserIdColumnInspector;
        this.jdbcTemplate = jdbcTemplate;
        this.messageRepository = messageRepository;
        this.realtimeBroadcastClient = realtimeBroadcastClient;
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
            if (distinctSorted.size() == 2) {
                Optional<Channel> existingOneToOne = findExistingOneToOneDm(request.workspaceKey(), distinctSorted);
                if (existingOneToOne.isPresent()) {
                    Channel ch = existingOneToOne.get();
                    ensureDmParticipantsMembers(ch, distinctSorted);
                    List<ChannelMember> members = channelMemberRepository.findByChannelId(ch.getId());
                    return toResponse(ch, members);
                }
            }
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

    private Optional<Channel> findExistingOneToOneDm(String workspaceKey, List<String> participantIds) {
        if (participantIds == null || participantIds.size() != 2) {
            return Optional.empty();
        }
        String expectedCanonicalName = buildDmCanonicalName(participantIds);
        String a = participantIds.get(0);
        String b = participantIds.get(1);
        Set<String> expected = Set.of(a, b);
        List<Channel> mine = channelRepository.findByMemberEmployeeNo(a);
        for (Channel ch : mine) {
            if (ch.getChannelType() != ChannelType.DM) {
                continue;
            }
            if (!Objects.equals(ch.getWorkspaceKey(), workspaceKey)) {
                continue;
            }
            if (Objects.equals(ch.getName(), expectedCanonicalName)) {
                return Optional.of(ch);
            }
            List<ChannelMember> members = channelMemberRepository.findByChannelId(ch.getId());
            Set<String> actual = members.stream()
                    .map(cm -> cm.getUser().getEmployeeNo() == null ? "" : cm.getUser().getEmployeeNo().trim())
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (actual.size() == 2 && actual.equals(expected)) {
                return Optional.of(ch);
            }
        }
        return Optional.empty();
    }

    public ChannelResponse getChannel(Long channelId) {
        Channel channel = channelRepository.findByIdWithCreatedBy(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        List<ChannelMember> members = channelMemberRepository.findByChannelIdFetchUsers(channelId);
        return toResponse(channel, members);
    }

    @Transactional
    public ChannelResponse joinChannel(Long channelId, UserPrincipal principal, JoinChannelRequest request) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("채널을 찾을 수 없습니다."));
        User user = userRepository.findByEmployeeNo(request.employeeNo())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        User creator = channel.getCreatedBy();
        String creatorEmp = creator != null && creator.getEmployeeNo() != null
                ? creator.getEmployeeNo().trim()
                : "";
        boolean isDm = channel.getChannelType() == ChannelType.DM;
        if (!isDm && !creatorEmp.equals(actorEmp)) {
            throw new ForbiddenException("채널 개설자만 구성원을 추가할 수 있습니다.");
        }

        if (channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, request.employeeNo())) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }

        channelMemberRepository.save(new ChannelMember(channel, user, request.memberRole()));

        String joinerLabel = user.getName() != null && !user.getName().isBlank()
                ? user.getName().trim()
                : (user.getEmployeeNo() != null ? user.getEmployeeNo() : request.employeeNo());
        String joinSystemLine = "「" + joinerLabel + "」님이 채널에 참여했습니다.";
        saveAndBroadcastChannelSystemLine(channel, user, joinSystemLine);

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

    /**
     * 채널 개설자({@code channels.created_by})만 다른 멤버를 내보낼 수 있다. 개설자 본인은 제거할 수 없다.
     */
    @Transactional
    public ChannelResponse removeMember(Long channelId, UserPrincipal principal, String targetEmployeeNoRaw) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        String targetEmp = targetEmployeeNoRaw == null ? "" : targetEmployeeNoRaw.trim();
        if (targetEmp.isBlank()) {
            throw new IllegalArgumentException("내보낼 사용자 사원번호(targetEmployeeNo)가 필요합니다.");
        }

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        User createdBy = channel.getCreatedBy();
        if (createdBy == null || createdBy.getEmployeeNo() == null || createdBy.getEmployeeNo().isBlank()) {
            throw new IllegalStateException("채널 생성자 정보가 없습니다.");
        }
        String creatorEmp = createdBy.getEmployeeNo().trim();
        if (!creatorEmp.equals(actorEmp)) {
            throw new ForbiddenException("채널 개설자만 구성원을 내보낼 수 있습니다.");
        }
        if (creatorEmp.equals(targetEmp)) {
            throw new IllegalArgumentException("채널 개설자는 내보낼 수 없습니다.");
        }

        ChannelMember membership = channelMemberRepository
                .findByChannel_IdAndUser_EmployeeNo(channelId, targetEmp)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자는 채널 멤버가 아닙니다."));

        User actorUser = userRepository.findByEmployeeNo(actorEmp).orElse(createdBy);
        Long removedUserId = membership.getUser().getId();

        channelReadStateRepository.deleteByChannel_IdAndUser_EmployeeNo(channelId, targetEmp);
        channelMemberRepository.delete(membership);

        String targetLabel = userRepository.findByEmployeeNo(targetEmp)
                .map(u -> {
                    String n = u.getName();
                    return n != null && !n.isBlank() ? n.trim() : targetEmp;
                })
                .orElse(targetEmp);
        String systemLine = "「" + targetLabel + "」님을 채널에서 내보냈습니다.";
        saveAndBroadcastChannelSystemLine(channel, actorUser, systemLine);

        auditLogService.safeRecord(
                AuditEventType.CHANNEL_MEMBER_REMOVED,
                actorUser.getId(),
                "CHANNEL",
                channelId,
                channel.getWorkspaceKey(),
                "removedUserId=" + removedUserId + ",removedEmployeeNo=" + targetEmp,
                null
        );

        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        return toResponse(channel, members);
    }

    @Transactional
    public ChannelResponse renameGroupDm(Long channelId, UserPrincipal principal, String nameRaw) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        String name = nameRaw == null ? "" : nameRaw.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("DM 이름을 입력하세요.");
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        if (channel.getChannelType() != ChannelType.DM) {
            throw new IllegalArgumentException("DM 채널에서만 이름 변경이 가능합니다.");
        }
        if (!channelMemberRepository.existsByChannelIdAndUserEmployeeNo(channelId, actorEmp)) {
            throw new ForbiddenException("DM 멤버만 이름을 변경할 수 있습니다.");
        }
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        if (members.size() <= 2) {
            throw new IllegalArgumentException("1:1 DM은 이름을 변경할 수 없습니다.");
        }
        channel.updateDescription(name);
        channelRepository.save(channel);
        return toResponse(channel, members);
    }

    @Transactional
    public ChannelResponse renameChannel(Long channelId, UserPrincipal principal, String nameRaw) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        String name = nameRaw == null ? "" : nameRaw.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("채널 이름을 입력하세요.");
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        if (channel.getChannelType() == ChannelType.DM) {
            throw new IllegalArgumentException("DM 채널 이름은 별도 DM 이름 변경을 사용하세요.");
        }
        String creatorEmp = channel.getCreatedBy() == null || channel.getCreatedBy().getEmployeeNo() == null
                ? ""
                : channel.getCreatedBy().getEmployeeNo().trim();
        if (!creatorEmp.equals(actorEmp)) {
            throw new ForbiddenException("채널 관리자만 채널 이름을 변경할 수 있습니다.");
        }
        Optional<Channel> sameName = channelRepository.findByWorkspaceKeyAndName(channel.getWorkspaceKey(), name);
        if (sameName.isPresent() && !Objects.equals(sameName.get().getId(), channelId)) {
            throw new IllegalArgumentException("이미 존재하는 채널 이름입니다.");
        }
        channel.updateName(name);
        channelRepository.save(channel);
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        return toResponse(channel, members);
    }

    @Transactional
    public ChannelResponse delegateManager(Long channelId, UserPrincipal principal, String targetEmployeeNoRaw) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        String targetEmp = targetEmployeeNoRaw == null ? "" : targetEmployeeNoRaw.trim();
        if (targetEmp.isBlank()) {
            throw new IllegalArgumentException("위임 대상 사번이 필요합니다.");
        }
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        User creator = channel.getCreatedBy();
        String creatorEmp = creator == null || creator.getEmployeeNo() == null ? "" : creator.getEmployeeNo().trim();
        if (!creatorEmp.equals(actorEmp)) {
            throw new ForbiddenException("채널 관리자만 관리자 위임을 할 수 있습니다.");
        }
        if (targetEmp.equals(actorEmp)) {
            throw new IllegalArgumentException("본인에게는 위임할 수 없습니다.");
        }
        ChannelMember targetMember = channelMemberRepository.findByChannel_IdAndUser_EmployeeNo(channelId, targetEmp)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자는 채널 멤버가 아닙니다."));
        channel.transferManager(targetMember.getUser());
        channelRepository.save(channel);
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        return toResponse(channel, members);
    }

    @Transactional
    public void leaveChannel(Long channelId, UserPrincipal principal, String delegateManagerEmployeeNoRaw) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        ChannelMember me = channelMemberRepository.findByChannel_IdAndUser_EmployeeNo(channelId, actorEmp)
                .orElseThrow(() -> new ForbiddenException("채널 멤버만 나갈 수 있습니다."));

        String creatorEmp = channel.getCreatedBy() != null && channel.getCreatedBy().getEmployeeNo() != null
                ? channel.getCreatedBy().getEmployeeNo().trim()
                : "";
        boolean isCreator = !creatorEmp.isBlank() && creatorEmp.equals(actorEmp);
        if (isCreator && channel.getChannelType() != ChannelType.DM) {
            String delegateEmp = delegateManagerEmployeeNoRaw == null ? "" : delegateManagerEmployeeNoRaw.trim();
            if (delegateEmp.isBlank()) {
                throw new IllegalArgumentException("채널 관리자가 나가려면 관리자 위임 대상이 필요합니다.");
            }
            if (delegateEmp.equals(actorEmp)) {
                throw new IllegalArgumentException("본인에게는 위임할 수 없습니다.");
            }
            ChannelMember delegateMember = channelMemberRepository
                    .findByChannel_IdAndUser_EmployeeNo(channelId, delegateEmp)
                    .orElseThrow(() -> new IllegalArgumentException("위임 대상이 채널 멤버가 아닙니다."));
            channel.transferManager(delegateMember.getUser());
            channelRepository.save(channel);
            channelMemberRepository.delete(delegateMember);
            channelMemberRepository.save(new ChannelMember(channel, delegateMember.getUser(), ChannelMemberRole.MANAGER));
        }

        channelReadStateRepository.deleteByChannel_IdAndUser_EmployeeNo(channelId, actorEmp);
        channelMemberRepository.delete(me);

        List<ChannelMember> leftMembers = channelMemberRepository.findByChannelId(channelId);
        if (channel.getChannelType() == ChannelType.DM) {
            // DM은 멤버가 0명이 되어도 대화 이력을 보존하기 위해 채널 엔티티를 삭제하지 않는다.
            return;
        }
        if (leftMembers.isEmpty()) {
            channelRepository.delete(channel);
            return;
        }
        User actorUser = userRepository.findByEmployeeNo(actorEmp).orElse(channel.getCreatedBy());
        String actorLabel = actorUser != null && actorUser.getName() != null && !actorUser.getName().isBlank()
                ? actorUser.getName().trim()
                : actorEmp;
        saveAndBroadcastChannelSystemLine(channel, actorUser, "「" + actorLabel + "」님이 채널을 나갔습니다.");
    }

    @Transactional
    public void closeChannel(Long channelId, UserPrincipal principal) {
        if (principal == null || principal.employeeNo() == null || principal.employeeNo().isBlank()) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        String actorEmp = principal.employeeNo().trim();
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("채널을 찾을 수 없습니다. id=" + channelId));
        String creatorEmp = channel.getCreatedBy() != null && channel.getCreatedBy().getEmployeeNo() != null
                ? channel.getCreatedBy().getEmployeeNo().trim()
                : "";
        if (!creatorEmp.equals(actorEmp)) {
            throw new ForbiddenException("채널 관리자만 채널 폐쇄를 할 수 있습니다.");
        }
        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);
        for (ChannelMember m : members) {
            String emp = m.getUser() != null && m.getUser().getEmployeeNo() != null
                    ? m.getUser().getEmployeeNo().trim()
                    : "";
            if (!emp.isBlank()) {
                channelReadStateRepository.deleteByChannel_IdAndUser_EmployeeNo(channelId, emp);
            }
        }
        channelMemberRepository.deleteAll(members);
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

        List<Long> channelIds = channels.stream().map(Channel::getId).toList();
        Map<Long, OffsetDateTime> lastMsgAtByChannel = fetchLatestRootMessageTimes(channelIds);

        return channels.stream()
                .map(channel -> {
                    int memberCount = channelMemberRepository.findByChannelId(channel.getId()).size();
                    String summaryDescription = resolveChannelSummaryDescription(channel, emp);
                    int unread = resolveUnreadRootCount(channel.getId(), emp);
                    List<String> dmPeers = channel.getChannelType() == ChannelType.DM
                            ? resolveDmPeerEmployeeNos(channel.getId(), emp)
                            : List.of();
                    OffsetDateTime lastAt = lastMsgAtByChannel.get(channel.getId());
                    return new ChannelSummaryResponse(
                            channel.getId(),
                            channel.getWorkspaceKey(),
                            channel.getName(),
                            summaryDescription,
                            channel.getChannelType().name(),
                            memberCount,
                            channel.getCreatedAt(),
                            unread,
                            dmPeers,
                            lastAt
                    );
                })
                .toList();
    }

    private Map<Long, OffsetDateTime> fetchLatestRootMessageTimes(List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = messageRepository.findLatestRootMessageTimeByChannelIds(channelIds);
        Map<Long, OffsetDateTime> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            Long cid = ((Number) row[0]).longValue();
            OffsetDateTime at = coerceToOffsetDateTime(row[1]);
            if (at != null) {
                map.put(cid, at);
            }
        }
        return map;
    }

    private static OffsetDateTime coerceToOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant ins) {
            return OffsetDateTime.ofInstant(ins, java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return OffsetDateTime.ofInstant(ts.toInstant(), java.time.ZoneOffset.UTC);
        }
        return null;
    }

    /** DM 사이드바 프레즌스: 조회자(사번)를 제외한 참가자 사번, 정렬 */
    private List<String> resolveDmPeerEmployeeNos(Long channelId, String viewerEmployeeNo) {
        String viewer = viewerEmployeeNo == null ? "" : viewerEmployeeNo.trim();
        List<ChannelMember> members = channelMemberRepository.findByChannelIdFetchUsers(channelId);
        return members.stream()
                .map(cm -> {
                    User u = cm.getUser();
                    return u.getEmployeeNo() == null ? "" : u.getEmployeeNo().trim();
                })
                .filter(e -> !e.isEmpty())
                .filter(e -> viewer.isEmpty() || !viewer.equals(e))
                .sorted()
                .toList();
    }

    private int resolveUnreadRootCount(Long channelId, String employeeNo) {
        var opt = channelReadStateRepository.findByChannel_IdAndUser_EmployeeNo(channelId, employeeNo);
        Message cursor = opt.map(rs -> rs.getLastReadMessage()).orElse(null);
        OffsetDateTime cursorTime = cursor != null ? cursor.getCreatedAt() : null;
        Long cursorId = cursor != null ? cursor.getId() : null;
        long n;
        if (cursorTime == null || cursorId == null) {
            n = messageRepository.countRootMessagesInChannel(channelId);
        } else {
            n = messageRepository.countRootMessagesNewerThanCursor(channelId, cursorTime, cursorId);
        }
        if (n > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) n;
    }

    /**
     * 사이드바용 요약: DM은 DB {@code description}(생성 시점 1인 시점 라벨) 대신,
     * 조회 중인 사용자를 제외한 참가자 표시명을 붙인다(각자 상대방 이름이 보이게).
     */
    private String resolveChannelSummaryDescription(Channel channel, String viewerEmployeeNo) {
        if (channel.getChannelType() != ChannelType.DM) {
            return channel.getDescription();
        }
        int dmMemberCount = channelMemberRepository.findByChannelId(channel.getId()).size();
        String custom = channel.getDescription() == null ? "" : channel.getDescription().trim();
        if (dmMemberCount > 2 && !custom.isBlank()) {
            return custom;
        }
        String peerLabel = buildDmPeerDisplayLabel(channel.getId(), viewerEmployeeNo);
        if (!peerLabel.isBlank() && !"DM".equals(peerLabel)) {
            return peerLabel;
        }
        return custom.isBlank() ? "DM" : custom;
    }

    private String buildDmPeerDisplayLabel(Long channelId, String viewerEmployeeNo) {
        List<ChannelMember> members = channelMemberRepository.findByChannelIdFetchUsers(channelId);
        String viewer = viewerEmployeeNo == null ? "" : viewerEmployeeNo.trim();
        List<String> labels = new ArrayList<>();
        for (ChannelMember cm : members) {
            User u = cm.getUser();
            String emp = u.getEmployeeNo() == null ? "" : u.getEmployeeNo().trim();
            if (!viewer.isEmpty() && viewer.equals(emp)) {
                continue;
            }
            String name = u.getName();
            if (name != null && !name.isBlank()) {
                labels.add(name.trim());
            } else if (!emp.isEmpty()) {
                labels.add(emp);
            }
        }
        if (labels.isEmpty()) {
            return "DM";
        }
        return String.join(", ", labels);
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
        Map<String, String> byEmp = rows.stream().collect(Collectors.toMap(
                m -> m.getUser().getEmployeeNo(),
                m -> {
                    String dn = m.getGroup() != null ? m.getGroup().getDisplayName() : null;
                    String t = dn == null ? "" : dn.trim();
                    // placeholder(예: display_name='TEAM')가 그대로 내려오는 경우를 대비
                    if (t.isEmpty()) return "";
                    if (t.equalsIgnoreCase(memberGroupType)) return "";
                    return t;
                },
                (a, b) -> a
        ));
        if (byEmp.size() < employeeNos.size()) {
            // JPA 매핑/데이터 편차(공백, 레거시 행 등)로 일부 누락될 때 DB 원본 조인으로 보강
            Map<String, String> fallback = membershipDisplayByEmpJdbc(memberGroupType, employeeNos);
            fallback.forEach(byEmp::putIfAbsent);
        }
        return byEmp;
    }

    private Map<String, String> membershipDisplayByEmpJdbc(String memberGroupType, List<String> employeeNos) {
        if (employeeNos.isEmpty()) return Map.of();
        String placeholders = IntStream.range(0, employeeNos.size())
                .mapToObj(i -> "?")
                .collect(Collectors.joining(","));
        String sql = """
                SELECT u.employee_no AS employee_no,
                       og.display_name AS display_name
                FROM org_group_members ogm
                JOIN users u ON u.employee_no = ogm.employee_no
                JOIN org_groups og ON og.group_code = ogm.group_code
                WHERE TRIM(LOWER(ogm.member_group_type)) = TRIM(LOWER(?))
                  AND u.employee_no IN (%s)
                """.formatted(placeholders);
        List<Object> params = new ArrayList<>(employeeNos.size() + 1);
        params.add(memberGroupType);
        params.addAll(employeeNos);
        Map<String, String> out = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String emp = rs.getString("employee_no");
            String dn = rs.getString("display_name");
            if (emp == null || emp.isBlank()) return;
            String t = dn == null ? "" : dn.trim();
            if (t.isEmpty()) return;
            if (t.equalsIgnoreCase(memberGroupType)) return;
            out.putIfAbsent(emp, t);
        }, params.toArray());
        return out;
    }

    /** {@code message_type=SYSTEM} 저장 후 커밋 시점에 실시간 브로드캐스트. */
    private void saveAndBroadcastChannelSystemLine(Channel channel, User technicalSender, String systemLine) {
        Message systemMsg = messageRepository.save(
                new Message(channel, technicalSender, null, systemLine, "SYSTEM"));
        String systemCreatedIso = systemMsg.getCreatedAt().toString();
        Long systemMessageId = systemMsg.getId();
        long cid = channel.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                realtimeBroadcastClient.broadcastChannelSystem(cid, systemLine, systemCreatedIso, systemMessageId);
            }
        });
    }
}
