package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TokenRedisRepository Testcontainers 통합 테스트.
 *
 * <p>실제 Redis 컨테이너를 띄워 RT 저장·조회·삭제와 AT Blacklist 등록·조회를 검증한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TokenRedisRepositoryTest {

    @Container
    static RedisContainer redisContainer = new RedisContainer(
        RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }

    @Autowired
    private TokenRedisRepository tokenRedisRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // 각 테스트 전 Redis 데이터 초기화
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("saveRefreshToken 저장 후 findRefreshToken으로 조회할 수 있다")
    void saveAndFindRefreshToken() {
        // given
        String username = "testuser";
        String refreshToken = "sample-refresh-token";
        long ttlSeconds = 3600L;

        // when
        tokenRedisRepository.saveRefreshToken(username, refreshToken, ttlSeconds);

        // then
        assertThat(tokenRedisRepository.findRefreshToken(username))
            .isPresent()
            .hasValue(refreshToken);
    }

    @Test
    @DisplayName("deleteRefreshToken 삭제 후 findRefreshToken은 Optional.empty()를 반환한다")
    void deleteRefreshToken_returnsEmpty() {
        // given
        tokenRedisRepository.saveRefreshToken("testuser", "token", 3600L);

        // when
        tokenRedisRepository.deleteRefreshToken("testuser");

        // then
        assertThat(tokenRedisRepository.findRefreshToken("testuser")).isEmpty();
    }

    @Test
    @DisplayName("saveRefreshToken 저장 시 Redis에 TTL이 설정된다")
    void saveRefreshToken_setsTtl() throws InterruptedException {
        // given
        String username = "ttluser";
        long ttlSeconds = 2L;

        // when
        tokenRedisRepository.saveRefreshToken(username, "token-value", ttlSeconds);

        // then: TTL이 0보다 큰 양수인지 확인
        Long ttl = stringRedisTemplate.getExpire("RT:" + username);
        assertThat(ttl).isNotNull().isGreaterThan(0L);
    }

    @Test
    @DisplayName("addToBlacklist 등록 후 isBlacklisted는 true를 반환한다")
    void addToBlacklist_andIsBlacklisted_returnsTrue() {
        // given
        String accessToken = "blacklisted-access-token";
        long remainingTtlMillis = 3600_000L;

        // when
        tokenRedisRepository.addToBlacklist(accessToken, remainingTtlMillis);

        // then
        assertThat(tokenRedisRepository.isBlacklisted(accessToken)).isTrue();
    }

    @Test
    @DisplayName("Blacklist에 없는 토큰에 대해 isBlacklisted는 false를 반환한다")
    void isBlacklisted_returnsFalse_forUnregisteredToken() {
        // given
        String accessToken = "not-blacklisted-token";

        // when & then
        assertThat(tokenRedisRepository.isBlacklisted(accessToken)).isFalse();
    }
}
