package com.ech.backend.domain.message;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 스레드 답글 조회 — 아카이브된 메시지 제외 */
    @Query("""
            SELECT m FROM Message m
            WHERE m.parentMessage.id = :parentMessageId
              AND m.archivedAt IS NULL
            ORDER BY m.createdAt ASC
            """)
    List<Message> findByParentMessageIdOrderByCreatedAtAsc(@Param("parentMessageId") Long parentMessageId);

    Optional<Message> findByIdAndChannel_Id(Long id, Long channelId);

    /** 채널의 최근 메시지 조회 (스레드 루트 메시지만, 아카이브/삭제 제외) */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender s
            WHERE m.channel.id = :channelId
              AND m.parentMessage IS NULL
              AND m.archivedAt IS NULL
              AND m.isDeleted = false
            ORDER BY m.createdAt DESC
            """)
    List<Message> findRecentByChannelId(@Param("channelId") Long channelId, Pageable pageable);

    /**
     * 타임라인(루트 + REPLY) 최신 N개.
     * - ROOT: parentMessage IS NULL (기존 메인 타임라인)
     * - REPLY: parentMessage는 존재하며 messageType이 REPLY_* 인 메시지
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender s
            LEFT JOIN FETCH m.parentMessage pm
            LEFT JOIN FETCH pm.sender pms
            WHERE m.channel.id = :channelId
              AND m.isDeleted = false
              AND m.archivedAt IS NULL
              AND (m.parentMessage IS NULL OR m.messageType LIKE 'REPLY%')
            ORDER BY m.createdAt DESC
            """)
    List<Message> findTimelineByChannelId(@Param("channelId") Long channelId, Pageable pageable);

    /**
     * 통합 검색: 사용자가 속한 채널의 메시지 본문을 키워드로 검색.
     * 아카이브/삭제 메시지는 제외된다.
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.channel ch
            WHERE LOWER(m.body) LIKE LOWER(CONCAT('%', :keyword, '%'))
              AND m.archivedAt IS NULL
              AND m.isDeleted = false
              AND m.parentMessage IS NULL
              AND UPPER(COALESCE(m.messageType, '')) NOT LIKE 'COMMENT%'
              AND UPPER(COALESCE(m.messageType, '')) NOT LIKE 'REPLY%'
              AND UPPER(COALESCE(m.messageType, '')) NOT LIKE 'FILE%'
              AND EXISTS (
                SELECT cm FROM ChannelMember cm
                WHERE cm.channel.id = ch.id
                  AND cm.user.employeeNo = :employeeNo
              )
            ORDER BY m.createdAt DESC
            """)
    List<Message> searchInJoinedChannels(@Param("keyword") String keyword,
                                         @Param("employeeNo") String employeeNo,
                                         Pageable pageable);

    /**
     * 통합 검색: 사용자가 속한 채널의 댓글(COMMENT_*) 본문을 키워드로 검색.
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.channel ch
            JOIN FETCH m.parentMessage p
            WHERE LOWER(m.body) LIKE LOWER(CONCAT('%', :keyword, '%'))
              AND m.archivedAt IS NULL
              AND m.isDeleted = false
              AND m.parentMessage IS NOT NULL
              AND UPPER(m.messageType) LIKE 'COMMENT%'
              AND EXISTS (
                SELECT cm FROM ChannelMember cm
                WHERE cm.channel.id = ch.id
                  AND cm.user.employeeNo = :employeeNo
              )
            ORDER BY m.createdAt DESC
            """)
    List<Message> searchCommentsInJoinedChannels(@Param("keyword") String keyword,
                                                 @Param("employeeNo") String employeeNo,
                                                 Pageable pageable);

    /**
     * 보존 정책 적용: 지정 기간 이전의 메시지를 아카이브 처리 (soft delete).
     * 이미 아카이브된 메시지와 삭제된 메시지는 건드리지 않는다.
     */
    @Modifying
    @Query("""
            UPDATE Message m
            SET m.archivedAt = :now
            WHERE m.createdAt < :cutoff
              AND m.archivedAt IS NULL
              AND m.isDeleted = false
            """)
    int archiveOlderThan(@Param("cutoff") OffsetDateTime cutoff, @Param("now") OffsetDateTime now);

    /**
     * 메인 타임라인(루트) 메시지 중 읽음 포인터 이후 건수. {@code afterId}가 {@code null}이면 전체 루트 메시지 수.
     */
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.channel.id = :channelId
              AND m.parentMessage IS NULL
              AND m.archivedAt IS NULL
              AND m.isDeleted = false
              AND (:afterId IS NULL OR m.id > :afterId)
            """)
    long countRootMessagesAfter(@Param("channelId") long channelId, @Param("afterId") Long afterId);

    /**
     * 채널별 메인 타임라인(루트) 최신 메시지 시각. 미읽음 퀵 메뉴 정렬용.
     * {@code channelIds}가 비어 있으면 호출하지 않는다.
     */
    @Query("""
            SELECT m.channel.id, MAX(m.createdAt)
            FROM Message m
            WHERE m.channel.id IN :channelIds
              AND m.parentMessage IS NULL
              AND m.archivedAt IS NULL
              AND m.isDeleted = false
            GROUP BY m.channel.id
            """)
    List<Object[]> findLatestRootMessageTimeByChannelIds(@Param("channelIds") Collection<Long> channelIds);

    /**
     * 원글 스레드 활동: 루트에 직접 달린 댓글(COMMENT_*), 루트에 직접 달린 답글(REPLY_*),
     * 댓글에 달린 답글(REPLY_*, 부모의 부모가 루트)까지 포함 — 건수·최신 시각 집계용.
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender s
            JOIN FETCH m.parentMessage p
            LEFT JOIN FETCH p.parentMessage pRoot
            WHERE m.archivedAt IS NULL
              AND m.isDeleted = false
              AND (
                (p.id IN :rootIds AND UPPER(m.messageType) LIKE 'COMMENT%')
                OR (p.id IN :rootIds AND UPPER(m.messageType) LIKE 'REPLY%' AND pRoot IS NULL)
                OR (UPPER(m.messageType) LIKE 'REPLY%' AND pRoot IS NOT NULL AND pRoot.id IN :rootIds)
              )
            """)
    List<Message> findThreadActivityUnderRoots(@Param("rootIds") Collection<Long> rootIds);

    /**
     * 스레드 모아보기: 댓글(COMMENT_*)·답글(REPLY_*, 루트 직속 또는 댓글 하위)이 하나라도 있는
     * 루트 메시지 id만, 마지막 스레드 활동 시각 기준 내림차순.
     */
    @Query(
            value = """
                    SELECT r.id
                    FROM messages r
                    WHERE r.channel_id = :channelId
                      AND r.parent_message_id IS NULL
                      AND r.archived_at IS NULL
                      AND r.is_deleted = false
                      AND EXISTS (
                          SELECT 1
                          FROM messages c
                          WHERE c.channel_id = r.channel_id
                            AND c.archived_at IS NULL
                            AND c.is_deleted = false
                            AND (
                                (c.parent_message_id = r.id
                                  AND (UPPER(COALESCE(c.message_type, '')) LIKE 'COMMENT%'
                                       OR UPPER(COALESCE(c.message_type, '')) LIKE 'REPLY%'))
                                OR (
                                  UPPER(COALESCE(c.message_type, '')) LIKE 'REPLY%'
                                  AND EXISTS (
                                      SELECT 1 FROM messages p
                                      WHERE p.id = c.parent_message_id
                                        AND p.parent_message_id = r.id
                                  )
                                )
                            )
                      )
                    ORDER BY (
                        SELECT MAX(c2.created_at)
                        FROM messages c2
                        WHERE c2.channel_id = r.channel_id
                          AND c2.archived_at IS NULL
                          AND c2.is_deleted = false
                          AND (
                              (c2.parent_message_id = r.id
                                AND (UPPER(COALESCE(c2.message_type, '')) LIKE 'COMMENT%'
                                     OR UPPER(COALESCE(c2.message_type, '')) LIKE 'REPLY%'))
                              OR (
                                  UPPER(COALESCE(c2.message_type, '')) LIKE 'REPLY%'
                                  AND EXISTS (
                                      SELECT 1 FROM messages p2
                                      WHERE p2.id = c2.parent_message_id
                                        AND p2.parent_message_id = r.id
                                  )
                              )
                          )
                    ) DESC
                    LIMIT :lim
                    """,
            nativeQuery = true)
    List<Long> findThreadRootIdsByChannelOrderByLastActivity(@Param("channelId") long channelId, @Param("lim") int lim);
}
