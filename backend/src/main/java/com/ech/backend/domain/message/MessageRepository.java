package com.ech.backend.domain.message;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
}
