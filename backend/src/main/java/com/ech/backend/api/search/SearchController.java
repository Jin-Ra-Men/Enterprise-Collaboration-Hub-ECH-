package com.ech.backend.api.search;

import com.ech.backend.api.search.dto.SearchResponse;
import com.ech.backend.api.search.dto.SearchType;
import com.ech.backend.api.auth.AuthService;
import com.ech.backend.common.api.ApiResponse;
import com.ech.backend.common.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final AuthService authService;

    public SearchController(SearchService searchService, AuthService authService) {
        this.searchService = searchService;
        this.authService = authService;
    }

    /**
     * 통합 검색 API.
     * JWT 인증이 필요하며, 채널 메시지/파일은 본인이 속한 채널만 검색된다.
     *
     * @param q      검색 키워드 (2자 이상)
     * @param type   검색 유형 (ALL / MESSAGES / COMMENTS / CHANNELS / FILES / WORK_ITEMS / KANBAN_CARDS, 기본 ALL)
     * @param limit  결과 최대 건수 (1~50, 기본 20)
     */
    @GetMapping
    public ApiResponse<SearchResponse> search(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SearchType searchType;
        try {
            searchType = SearchType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            searchType = SearchType.ALL;
        }

        String employeeNo = null;
        if (principal != null) {
            employeeNo = authService.findUserForPrincipal(principal).map(u -> u.getEmployeeNo()).orElse(null);
        }
        return ApiResponse.success(searchService.search(q, searchType, employeeNo, limit));
    }
}
