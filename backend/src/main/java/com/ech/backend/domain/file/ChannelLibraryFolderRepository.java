package com.ech.backend.domain.file;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelLibraryFolderRepository extends JpaRepository<ChannelLibraryFolder, Long> {

    List<ChannelLibraryFolder> findByChannel_IdOrderBySortOrderAscNameAsc(Long channelId);

    Optional<ChannelLibraryFolder> findByIdAndChannel_Id(Long id, Long channelId);

    int countByChannel_Id(Long channelId);
}
