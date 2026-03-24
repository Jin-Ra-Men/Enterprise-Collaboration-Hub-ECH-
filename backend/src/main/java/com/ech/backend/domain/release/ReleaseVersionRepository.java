package com.ech.backend.domain.release;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseVersionRepository extends JpaRepository<ReleaseVersion, Long> {

    Optional<ReleaseVersion> findByVersion(String version);

    Optional<ReleaseVersion> findByStatus(ReleaseStatus status);

    List<ReleaseVersion> findAllByOrderByUploadedAtDesc();

    /** 롤백 대상: 이전 운영 버전(PREVIOUS) 중 가장 최근 활성화된 것 */
    Optional<ReleaseVersion> findTopByStatusOrderByActivatedAtDesc(ReleaseStatus status);
}
