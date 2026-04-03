package com.ech.backend.api.admin.org;

import com.ech.backend.api.admin.org.dto.OrgGroupResponse;
import com.ech.backend.api.admin.org.dto.OrgGroupSaveRequest;
import com.ech.backend.common.exception.NotFoundException;
import com.ech.backend.domain.org.OrgGroup;
import com.ech.backend.domain.org.OrgGroupMemberRepository;
import com.ech.backend.domain.org.OrgGroupRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrgService {

    private final OrgGroupRepository orgGroupRepository;
    private final OrgGroupMemberRepository orgGroupMemberRepository;

    public AdminOrgService(OrgGroupRepository orgGroupRepository,
                           OrgGroupMemberRepository orgGroupMemberRepository) {
        this.orgGroupRepository = orgGroupRepository;
        this.orgGroupMemberRepository = orgGroupMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<OrgGroupResponse> getAllOrgGroups() {
        return orgGroupRepository.findAllByOrderByGroupTypeAscSortOrderAscDisplayNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrgGroupResponse createOrgGroup(OrgGroupSaveRequest req) {
        String code = req.groupCode().trim();
        if (orgGroupRepository.findByGroupCode(code).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 그룹 코드입니다: " + code);
        }
        String parentCode = blank(req.memberOfGroupCode()) ? null : req.memberOfGroupCode().trim();
        String path = computeGroupPath(code, parentCode);
        OrgGroup group = new OrgGroup(
                req.groupType().trim(), code, req.displayName().trim(), parentCode, path);
        if (req.sortOrder() != null) group.setSortOrder(req.sortOrder());
        if (req.isActive() != null)  group.setIsActive(req.isActive());
        orgGroupRepository.save(group);
        return toResponse(group);
    }

    @Transactional
    public OrgGroupResponse updateOrgGroup(String groupCode, OrgGroupSaveRequest req) {
        OrgGroup group = orgGroupRepository.findByGroupCode(groupCode.trim())
                .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다: " + groupCode));

        // 그룹코드 변경 처리
        String newCode = blank(req.groupCode()) ? groupCode.trim() : req.groupCode().trim();
        if (!newCode.equals(groupCode.trim())) {
            if (orgGroupRepository.findByGroupCode(newCode).isPresent()) {
                throw new IllegalArgumentException("이미 존재하는 그룹 코드입니다: " + newCode);
            }
            // FK 참조 먼저 갱신 (cascade update 미지원 환경 대응)
            orgGroupMemberRepository.updateGroupCode(groupCode.trim(), newCode);
            orgGroupRepository.updateMemberOfGroupCode(groupCode.trim(), newCode);
            group.setGroupCode(newCode);
        }

        String newDisplayName = req.displayName().trim();
        String newParentCode  = blank(req.memberOfGroupCode()) ? null : req.memberOfGroupCode().trim();

        group.setDisplayName(newDisplayName);
        group.setMemberOfGroupCode(newParentCode);
        String path = computeGroupPath(group.getGroupCode(), newParentCode);
        group.setGroupPath(path);
        if (req.sortOrder() != null) group.setSortOrder(req.sortOrder());
        if (req.isActive() != null)  group.setIsActive(req.isActive());
        orgGroupRepository.save(group);

        // 하위 조직 group_path 재계산
        updateDescendantPaths(group.getGroupCode(), path);

        return toResponse(group);
    }

    @Transactional
    public void deleteOrgGroup(String groupCode) {
        OrgGroup group = orgGroupRepository.findByGroupCode(groupCode.trim())
                .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다: " + groupCode));
        deleteRecursive(group);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private void deleteRecursive(OrgGroup group) {
        // 자식 먼저 재귀 삭제
        List<OrgGroup> children = orgGroupRepository.findAllByMemberOfGroupCode(group.getGroupCode());
        for (OrgGroup child : children) {
            deleteRecursive(child);
        }
        // 이 그룹을 참조하는 OrgGroupMember 먼저 제거
        orgGroupMemberRepository.deleteAllByGroupCode(group.getGroupCode());
        orgGroupRepository.delete(group);
    }

    /**
     * group_path를 그룹 코드 세미콜론(;) 체인으로 계산한다.
     * 예) 최상위: "ORGROOT"  /  2단계: "ORGROOT;ORG"  /  3단계: "ORGROOT;ORG;TEAM_CODE"
     * JOB_LEVEL/JOB_POSITION/JOB_TITLE 같이 부모 없는 항목은 코드 단독.
     */
    private String computeGroupPath(String groupCode, String parentCode) {
        if (blank(parentCode)) return groupCode;
        OrgGroup parent = orgGroupRepository.findByGroupCode(parentCode).orElse(null);
        if (parent == null) return groupCode;
        String parentPath = blank(parent.getGroupPath()) ? parent.getGroupCode() : parent.getGroupPath();
        return parentPath + ";" + groupCode;
    }

    /** 코드·경로 변경 시 모든 하위 그룹의 group_path를 재귀적으로 갱신한다. */
    private void updateDescendantPaths(String parentCode, String parentPath) {
        List<OrgGroup> children = orgGroupRepository.findAllByMemberOfGroupCode(parentCode);
        for (OrgGroup child : children) {
            String newPath = parentPath + ";" + child.getGroupCode();
            child.setGroupPath(newPath);
            orgGroupRepository.save(child);
            updateDescendantPaths(child.getGroupCode(), newPath);
        }
    }

    private OrgGroupResponse toResponse(OrgGroup g) {
        return new OrgGroupResponse(
                g.getId(), g.getGroupType(), g.getGroupCode(), g.getDisplayName(),
                g.getMemberOfGroupCode(), g.getGroupPath(), g.getSortOrder(), g.isActive());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
