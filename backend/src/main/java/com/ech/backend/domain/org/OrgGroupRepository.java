package com.ech.backend.domain.org;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgGroupRepository extends JpaRepository<OrgGroup, Long> {

    Optional<OrgGroup> findByGroupTypeAndGroupCode(String groupType, String groupCode);

    List<OrgGroup> findAllByGroupTypeAndIsActiveOrderByDisplayNameAsc(String groupType, boolean isActive);

    List<OrgGroup> findAllByGroupTypeAndMemberOfGroupCodeAndIsActiveOrderByDisplayNameAsc(
            String groupType,
            String memberOfGroupCode,
            boolean isActive
    );

    Optional<OrgGroup> findByGroupCode(String groupCode);

    /** 관리자 조직 관리용: 모든 그룹을 타입·정렬순·이름 순서로 조회 */
    List<OrgGroup> findAllByOrderByGroupTypeAscSortOrderAscDisplayNameAsc();

    /** 특정 부모 코드를 가진 자식 그룹 조회 (경로 재계산·삭제 연쇄에 사용) */
    List<OrgGroup> findAllByMemberOfGroupCode(String memberOfGroupCode);
}

