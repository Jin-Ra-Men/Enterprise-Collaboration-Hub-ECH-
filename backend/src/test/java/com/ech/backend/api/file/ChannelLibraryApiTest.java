package com.ech.backend.api.file;

import com.ech.backend.BaseIntegrationTest;
import com.ech.backend.domain.channel.Channel;
import com.ech.backend.domain.channel.ChannelMember;
import com.ech.backend.domain.channel.ChannelMemberRepository;
import com.ech.backend.domain.channel.ChannelRepository;
import com.ech.backend.domain.channel.ChannelType;
import com.ech.backend.domain.user.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("채널 자료실 API")
class ChannelLibraryApiTest extends BaseIntegrationTest {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMemberRepository channelMemberRepository;

    @Test
    @DisplayName("폴더 생성·첨부 메타 갱신·필터 조회")
    void folder_and_file_library_meta() throws Exception {
        User admin = userRepository.findByEmployeeNo(adminEmployeeNo).orElseThrow();
        Channel ch = channelRepository.saveAndFlush(
                new Channel("WS_LIB", "자료실테스트채널", null, ChannelType.PUBLIC, admin));
        channelMemberRepository.saveAndFlush(
                new ChannelMember(ch, admin, com.ech.backend.domain.channel.ChannelMemberRole.MANAGER));

        MvcResult folderRes = mockMvc.perform(post("/api/channels/" + ch.getId() + "/library/folders")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"회의자료\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("회의자료"))
                .andReturn();
        Number folderIdNum = JsonPath.read(folderRes.getResponse().getContentAsString(), "$.data.id");
        long folderId = folderIdNum.longValue();

        String regFile = """
                {
                  "uploadedByEmployeeNo": "%s",
                  "originalFilename": "a.txt",
                  "contentType": "text/plain",
                  "sizeBytes": 4,
                  "storageKey": "legacy/test-a.txt"
                }
                """.formatted(adminEmployeeNo);

        MvcResult fileRes = mockMvc.perform(post("/api/channels/" + ch.getId() + "/files")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regFile))
                .andExpect(status().isOk())
                .andReturn();
        Number fileIdNum = JsonPath.read(fileRes.getResponse().getContentAsString(), "$.data.id");
        long fileId = fileIdNum.longValue();

        String patchLib = """
                {
                  "pinned": true,
                  "caption": "중요",
                  "tags": "공유,회의",
                  "folderId": %d,
                  "detachFolder": false
                }
                """.formatted(folderId);

        mockMvc.perform(patch("/api/channels/" + ch.getId() + "/library/files/" + fileId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchLib))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.libraryPinned").value(true))
                .andExpect(jsonPath("$.data.libraryCaption").value("중요"))
                .andExpect(jsonPath("$.data.libraryFolderName").value("회의자료"));

        mockMvc.perform(get("/api/channels/" + ch.getId() + "/files")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("libraryFolderId", String.valueOf(folderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(delete("/api/channels/" + ch.getId() + "/library/folders/" + folderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/channels/" + ch.getId() + "/files")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("employeeNo", adminEmployeeNo)
                        .param("libraryUncategorizedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].libraryFolderId").value(nullValue()));
    }
}
