package com.ech.backend.api.search;

import com.ech.backend.api.search.dto.SearchResponse;
import com.ech.backend.api.search.dto.SearchResultItem;
import com.ech.backend.api.search.dto.SearchType;
import com.ech.backend.common.mention.MentionParser;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.file.ChannelFileRepository;
import com.ech.backend.domain.kanban.KanbanCardRepository;
import com.ech.backend.domain.message.Message;
import com.ech.backend.domain.message.MessageRepository;
import com.ech.backend.domain.work.WorkItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RAW_PREVIEW_MAX = 10_000;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_PER_TYPE = 10;
    private static final int PREVIEW_LENGTH = 150;

    private final MessageRepository messageRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelRepository channelRepository;
    private final ChannelFileRepository channelFileRepository;
    private final WorkItemRepository workItemRepository;
    private final KanbanCardRepository kanbanCardRepository;

    /**
     * 스레드 모달 원글 ID — 부모 체인을 따라 올라가 parent가 없는 메시지(루트)의 id를 반환한다.
     */
    private static Long resolveThreadRootMessageId(Message comment) {
        if (comment == null) {
            return null;
        }
        Message cur = comment;
        int guard = 0;
        while (cur.getParentMessage() != null && guard++ < 32) {
            cur = cur.getParentMessage();
        }
        return cur.getId();
    }

    private String resolveChannelDisplayName(Channel channel, String viewerEmployeeNo) {
        if (channel == null) {
            return "채널";
        }
        String name = channel.getName();
        String description = channel.getDescription();
        if (channel.getChannelType() == ChannelType.DM) {
            String dmLabel = buildDmDisplayLabel(channel.getId(), viewerEmployeeNo);
            if (dmLabel != null && !dmLabel.isBlank()) {
                return dmLabel;
            }
            if (name != null && name.startsWith("__dm__")) {
                return "DM";
            }
            if (description != null && !description.isBlank()) {
                return description;
            }
        }
        return (name == null || name.isBlank()) ? "채널" : name;
    }

    private String buildDmDisplayLabel(Long channelId, String viewerEmployeeNo) {
        if (channelId == null) {
            return null;
        }
        String viewer = viewerEmployeeNo == null ? "" : viewerEmployeeNo.trim();
        List<ChannelMember> members = channelMemberRepository.findByChannelIdFetchUsers(channelId);
        List<String> labels = new ArrayList<>();
        for (ChannelMember cm : members) {
            if (cm.getUser() == null) {
                continue;
            }
            String emp = cm.getUser().getEmployeeNo() == null ? "" : cm.getUser().getEmployeeNo().trim();
            if (!viewer.isEmpty() && viewer.equals(emp)) {
                continue;
            }
            String display = cm.getUser().getName();
            if (display == null || display.isBlank()) {
                display = emp;
            }
            if (display != null && !display.isBlank()) {
                labels.add(display.trim());
            }
        }
        if (labels.isEmpty()) {
            return null;
        }
        if (labels.size() == 1) {
            return labels.get(0);
        }
        return labels.get(0) + " 외 " + (labels.size() - 1) + "명";
    }

    private static String normalizeMessageBodyForSearch(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String raw = body.trim();
        if (!(raw.startsWith("{") && raw.contains("\"kind\"") && raw.contains("FILE"))) {
            return MentionParser.previewForToast(raw, RAW_PREVIEW_MAX);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = OBJECT_MAPPER.readValue(raw, Map.class);
            if (payload == null) {
                return raw;
            }
            if (!"FILE".equals(String.valueOf(payload.get("kind")))) {
                return raw;
            }
            String filename = String.valueOf(payload.getOrDefault("originalFilename", "")).trim();
            if (!filename.isEmpty()) {
                return "첨부파일: " + MentionParser.previewForToast(filename, RAW_PREVIEW_MAX);
            }
            return "첨부파일";
        } catch (Exception ignored) {
            return MentionParser.previewForToast(raw, RAW_PREVIEW_MAX);
        }
    }

    public SearchService(
            MessageRepository messageRepository,
            ChannelMemberRepository channelMemberRepository,
            ChannelRepository channelRepository,
            ChannelFileRepository channelFileRepository,
            WorkItemRepository workItemRepository,
            KanbanCardRepository kanbanCardRepository
    ) {
        this.messageRepository = messageRepository;
        this.channelMemberRepository = channelMemberRepository;
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
                    .forEach(m -> {
                        String displayText = normalizeMessageBodyForSearch(m.getBody());
                        items.add(new SearchResultItem(
                                "MESSAGE",
                                m.getId(),
                                SearchResultItem.truncate(displayText, 60),
                                SearchResultItem.truncate(displayText, PREVIEW_LENGTH),
                                m.getChannel().getId(),
                                resolveChannelDisplayName(m.getChannel(), employeeNo),
                                m.getCreatedAt(),
                                null
                        ));
                    });
        }

        if (searchType == SearchType.ALL || searchType == SearchType.COMMENTS) {
            messageRepository.searchCommentsInJoinedChannels(kw, employeeNo, page)
                    .forEach(m -> {
                        String displayText = normalizeMessageBodyForSearch(m.getBody());
                        items.add(new SearchResultItem(
                                "COMMENT",
                                m.getId(),
                                SearchResultItem.truncate(displayText, 60),
                                SearchResultItem.truncate(displayText, PREVIEW_LENGTH),
                                m.getChannel().getId(),
                                resolveChannelDisplayName(m.getChannel(), employeeNo),
                                m.getCreatedAt(),
                                resolveThreadRootMessageId(m)
                        ));
                    });
        }

        if (searchType == SearchType.ALL || searchType == SearchType.CHANNELS) {
            channelRepository.searchByKeywordInJoinedChannels(kw, employeeNo, page)
                    .forEach(c -> {
                        String displayName = resolveChannelDisplayName(c, employeeNo);
                        items.add(new SearchResultItem(
                                "CHANNEL",
                                c.getId(),
                                displayName,
                                SearchResultItem.truncate(c.getDescription(), PREVIEW_LENGTH),
                                c.getId(),
                                displayName,
                                c.getCreatedAt(),
                                null
                        ));
                    });
        }

        if (searchType == SearchType.ALL || searchType == SearchType.FILES) {
            channelFileRepository.searchInJoinedChannels(kw, employeeNo, page)
                    .forEach(f -> items.add(new SearchResultItem(
                            "FILE",
                            f.getId(),
                            f.getOriginalFilename(),
                            f.getContentType() + "  ·  " + formatSize(f.getSizeBytes()),
                            f.getChannel().getId(),
                            resolveChannelDisplayName(f.getChannel(), employeeNo),
                            f.getCreatedAt(),
                            null
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
                            w.getCreatedAt(),
                            null
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
                            c.getCreatedAt(),
                            null
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
