package com.ech.backend;

import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 기반 클래스.
 * H2 인메모리 DB + MockMvc 사용. 각 테스트 전 테스트 사용자를 생성한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    protected static final String TEST_ADMIN_EMAIL = "admin_test@ech.com";
    protected static final String TEST_USER_EMAIL = "user_test@ech.com";
    protected static final String TEST_PASSWORD = "Test@1234!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected Long adminUserId;
    protected Long normalUserId;
    protected String adminToken;
    protected String userToken;

    @BeforeEach
    void setUpUsers() throws Exception {
        // 기존 테스트 계정이 있으면 재사용, 없으면 생성
        adminUserId = userRepository.findAll().stream()
                .filter(u -> TEST_ADMIN_EMAIL.equals(u.getEmail()))
                .map(User::getId)
                .findFirst()
                .orElseGet(() -> {
                    User admin = new User("TADM001", TEST_ADMIN_EMAIL, "테스트관리자", "IT", "ADMIN");
                    admin.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
                    return userRepository.saveAndFlush(admin).getId();
                });

        normalUserId = userRepository.findAll().stream()
                .filter(u -> TEST_USER_EMAIL.equals(u.getEmail()))
                .map(User::getId)
                .findFirst()
                .orElseGet(() -> {
                    User user = new User("TUSR001", TEST_USER_EMAIL, "테스트사용자", "개발팀", "MEMBER");
                    user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
                    return userRepository.saveAndFlush(user).getId();
                });

        adminToken = fetchToken(TEST_ADMIN_EMAIL, TEST_PASSWORD);
        userToken = fetchToken(TEST_USER_EMAIL, TEST_PASSWORD);
    }

    protected String fetchToken(String email, String password) throws Exception {
        String body = """
                {"loginId":"%s","password":"%s"}
                """.formatted(email, password);
        String response = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // JSON에서 token 추출
        var tree = objectMapper.readTree(response);
        return tree.path("data").path("token").asText();
    }
}
