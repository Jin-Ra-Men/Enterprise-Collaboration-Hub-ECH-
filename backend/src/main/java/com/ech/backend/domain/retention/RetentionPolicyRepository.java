package com.ech.backend.domain.retention;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {

    Optional<RetentionPolicy> findByResourceType(String resourceType);

    List<RetentionPolicy> findByIsEnabledTrue();
}
