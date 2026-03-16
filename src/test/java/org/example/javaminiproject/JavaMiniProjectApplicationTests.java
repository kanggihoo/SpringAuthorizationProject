package org.example.javaminiproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트 로드 테스트
 * - @ActiveProfiles("test")로 H2 인메모리 DB 사용 (PostgreSQL 연결 불필요)
 */
@SpringBootTest
@ActiveProfiles("test")
class JavaMiniProjectApplicationTests {

    @Test
    void contextLoads() {
    }

}
