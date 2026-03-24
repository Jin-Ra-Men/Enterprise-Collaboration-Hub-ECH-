package com.ech.backend.domain.message;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByParentMessageIdOrderByCreatedAtAsc(Long parentMessageId);

    Optional<Message> findByIdAndChannel_Id(Long id, Long channelId);
}
