package com.ech.backend.domain.release;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, Long> {

    List<DeploymentHistory> findAllByOrderByCreatedAtDesc();
}
