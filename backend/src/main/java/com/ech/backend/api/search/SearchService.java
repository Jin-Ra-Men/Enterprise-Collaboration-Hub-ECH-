package com.ech.backend.api.search;

import com.ech.backend.api.search.dto.SearchResponse;
import com.ech.backend.api.search.dto.SearchResultItem;
import com.ech.backend.api.search.dto.SearchType;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.kanban.KanbanCardRepository;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.work.WorkItemRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_PER_TYPE = 10;
    private static final int PREVIEW_LENGTH = 150;

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final ChannelFileRepository channelFileRepository;
    private final WorkItemRepository workItemRepository;
    private final KanbanCardRepository kanbanCardRepository;

    public SearchService(
            MessageRepository messageRepository,
            ChannelRepository channelRepository,
            ChannelFileRepository channelFileRepository,
            WorkItemRepository workItemRepository,
            KanbanCardRepository kanbanCardRepository
    ) {
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.channelFileRepository = channelFileRepository;
        this.workItemRepository = workItemRepository;
        this.kanbanCardRepository = kanbanCardRepository;
    }

    /**
     * 통합 검색을 실행한다.
     *
     * @param keyword  검색 키워드 (최소 2자)
     * @param type     검색 대상 유형 (null 또는 ALL = 전체)
     * @param employeeNo 요청자 사용자 사번 (채널 멤버십 필터링에 사용)
     * @param limit    최대 결과 건수 (1~50, 기본 20)
     */
    @Transactional(readOnly = true)
    public SearchResponse search(String keyword, SearchType type, String employeeNo, int limit) {
        if (keyword == null || keyword.trim().length() < 2) {
            throw new IllegalArgumentException("검색어는 2자 이상 입력해 주세요.");
        }

        int safeLimit = Math.min(Math.max(1, limit), MAX_LIMIT);
        String kw = keyword.trim();
        SearchType searchType = type == null ? SearchType.ALL : type;

        List<SearchResultItem> items = new ArrayList<>();
        PageRequest page = PageRequest.of(0, DEFAULT_PER_TYPE);

        if (searchType == SearchType.ALL || searchType == SearchType.MESSAGES) {
            messageRepository.searchInJoinedChannels(kw, employeeNo, page)
                    .forEach(m -> items.add(new SearchResultItem(
                            "MESSAGE",
                            m.getId(),
                            SearchResultItem.truncate(m.getBody(), 60),
                            SearchResultItem.truncate(m.getBody(), PREVIEW_LENGTH),
                            m.getChannel().getId(),
                            m.getChannel().getName(),
                            m.getCreatedAt()
                    )));
        }

        if (searchType == SearchType.ALL || searchType == SearchType.COMMENTS) {
            messageRepository.searchCommentsInJoinedChannels(kw, employeeNo, page)
                    .forEach(m -> items.add(new SearchResultItem(
                            "COMMENT",
                            m.getId(),
                            SearchResultItem.truncate(m.getBody(), 60),
                            SearchResultItem.truncate(m.getBody(), PREVIEW_LENGTH),
                            m.getChannel().getId(),
                            m.getChannel().getName(),
                            m.getCreatedAt()
                    )));
        }

        if (searchType == SearchType.ALL || searchType == SearchType.CHANNELS) {
            channelRepository.searchByKeywordInJoinedChannels(kw, employeeNo, page)
                    .forEach(c -> items.add(new SearchResultItem(
                            "CHANNEL",
                            c.getId(),
                            c.getName(),
                            SearchResultItem.truncate(c.getDescription(), PREVIEW_LENGTH),
                            c.getId(),
                            c.getName(),
                            c.getCreatedAt()
                    )));
        }

        if (searchType == SearchType.ALL || searchType == SearchType.FILES) {
            channelFileRepository.searchInJoinedChannels(kw, employeeNo, page)
                    .forEach(f -> items.add(new SearchResultItem(
                            "FILE",
                            f.getId(),
                            f.getOriginalFilename(),
                            f.getContentType() + "  ·  " + formatSize(f.getSizeBytes()),
                            f.getChannel().getId(),
                            f.getChannel().getName(),
                            f.getCreatedAt()
                    )));
        }

        if (searchType == SearchType.ALL || searchType == SearchType.WORK_ITEMS) {
            workItemRepository.searchByKeyword(kw, page)
                    .forEach(w -> items.add(new SearchResultItem(
                            "WORK_ITEM",
                            w.getId(),
                            w.getTitle(),
                            SearchResultItem.truncate(w.getDescription(), PREVIEW_LENGTH),
                            w.getSourceChannel().getId(),
                            w.getSourceChannel().getName(),
                            w.getCreatedAt()
                    )));
        }

        if (searchType == SearchType.ALL || searchType == SearchType.KANBAN_CARDS) {
            kanbanCardRepository.searchByKeyword(kw, page)
                    .forEach(c -> items.add(new SearchResultItem(
                            "KANBAN_CARD",
                            c.getId(),
                            c.getTitle(),
                            SearchResultItem.truncate(c.getDescription(), PREVIEW_LENGTH),
                            c.getColumn().getBoard().getId(),
                            c.getColumn().getBoard().getName(),
                            c.getCreatedAt()
                    )));
        }

        // 최신순 정렬 후 limit 적용
        items.sort(Comparator.comparing(SearchResultItem::createdAt).reversed());
        List<SearchResultItem> paged = items.stream().limit(safeLimit).toList();

        return new SearchResponse(keyword, searchType.name(), paged.size(), paged);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
