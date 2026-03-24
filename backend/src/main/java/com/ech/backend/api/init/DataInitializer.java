package com.ech.backend.api.init;

import com.ech.backend.domain.user.User;
import com.ech.backend.domain.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 구동 시 테스트 계정의 초기 비밀번호를 자동으로 설정한다.
 *
 * <p>비밀번호가 없는(null) 사용자에 한해 한 번만 설정되며, 이미 설정된 경우 덮어쓰지 않는다.
 * <ul>
 *   <li>기본 비밀번호: {@code Test1234!}</li>
 * </ul>
 *
 * <p><b>운영 전환 시 주의:</b> 그룹웨어 인증(GroupwareAuthProvider) 도입 후에는
 * 로컬 비밀번호가 의미 없어지므로 이 초기화 로직을 비활성화하거나 삭제한다.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> usersWithoutPassword = userRepository.findUsersWithoutPassword();
        if (usersWithoutPassword.isEmpty()) {
            return;
        }

        String encodedDefault = passwordEncoder.encode(DEFAULT_PASSWORD);
        for (User user : usersWithoutPassword) {
            user.setPasswordHash(encodedDefault);
        }
        userRepository.saveAll(usersWithoutPassword);
        log.info("[DataInitializer] 비밀번호 미설정 사용자 {}명에게 기본 비밀번호 적용 완료. (Test1234!)",
                usersWithoutPassword.size());
    }
}
