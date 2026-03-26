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
}

