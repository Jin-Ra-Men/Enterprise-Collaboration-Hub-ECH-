package com.ech.backend.domain.file;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelFileRepository extends JpaRepository<ChannelFile, Long> {
    List<ChannelFile> findByChannel_IdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    Optional<ChannelFile> findByIdAndChannel_Id(Long id, Long channelId);
}
