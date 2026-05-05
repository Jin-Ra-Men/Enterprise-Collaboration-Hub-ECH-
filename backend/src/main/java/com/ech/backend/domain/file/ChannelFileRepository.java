package com.ech.backend.domain.file;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelFileRepository extends JpaRepository<ChannelFile, Long> {

    List<ChannelFile> findByChannel_IdOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    @Query("""
            SELECT DISTINCT f FROM ChannelFile f
            LEFT JOIN FETCH f.libraryFolder lf
            WHERE f.channel.id = :channelId
            ORDER BY f.libraryPinned DESC, f.createdAt DESC
            """)
    List<ChannelFile> findHubByChannelOrderPinned(@Param("channelId") Long channelId, Pageable pageable);

    @Query("""
            SELECT DISTINCT f FROM ChannelFile f
            LEFT JOIN FETCH f.libraryFolder lf
            WHERE f.channel.id = :channelId AND f.libraryFolder.id = :folderId
            ORDER BY f.libraryPinned DESC, f.createdAt DESC
            """)
    List<ChannelFile> findHubByChannelAndFolder(
            @Param("channelId") Long channelId,
            @Param("folderId") Long folderId,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT f FROM ChannelFile f
            LEFT JOIN FETCH f.libraryFolder lf
            WHERE f.channel.id = :channelId AND f.libraryFolder IS NULL
            ORDER BY f.libraryPinned DESC, f.createdAt DESC
            """)
    List<ChannelFile> findHubByChannelUncategorized(@Param("channelId") Long channelId, Pageable pageable);

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

    /**
     * 사용자 삭제: 삭제될 채널(created_by = empNo)에 속한 파일 전체 삭제.
     * channel_files.channel_id → channels.id FK에 ON DELETE CASCADE가 없는 경우 대비.
     */
    @Modifying
    @Query(value = """
            DELETE FROM channel_files
            WHERE channel_id IN (SELECT id FROM channels WHERE created_by = :empNo)
            """, nativeQuery = true)
    void deleteByChannelCreatorEmployeeNo(@Param("empNo") String employeeNo);

    /** 사용자 삭제: 해당 사용자가 업로드한 파일 메타데이터 삭제 (다른 채널 소속 포함) */
    @Modifying
    @Query(value = "DELETE FROM channel_files WHERE uploaded_by = :empNo", nativeQuery = true)
    void deleteByUploaderEmployeeNo(@Param("empNo") String employeeNo);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChannelFile f SET f.libraryFolder = null WHERE f.libraryFolder.id = :folderId")
    void detachFilesFromLibraryFolder(@Param("folderId") Long folderId);
}
