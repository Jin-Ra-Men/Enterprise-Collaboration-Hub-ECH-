package com.ech.backend.domain.file;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelFileRepository extends JpaRepository<ChannelFile, Long> {

    List<ChannelFile> findByChannel_IdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    Optional<ChannelFile> findByIdAndChannel_Id(Long id, Long channelId);

    /**
     * 통합 검색: 사용자가 속한 채널의 파일명을 키워드로 검색.
     */
    @Query("""
            SELECT f FROM ChannelFile f
            JOIN FETCH f.channel ch
            WHERE LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))
              AND EXISTS (
                SELECT cm FROM ChannelMember cm
                WHERE cm.channel.id = ch.id
                  AND cm.user.employeeNo = :employeeNo
              )
            ORDER BY f.createdAt DESC
            """)
    List<ChannelFile> searchInJoinedChannels(@Param("keyword") String keyword,
                                              @Param("employeeNo") String employeeNo,
                                              Pageable pageable);
}
