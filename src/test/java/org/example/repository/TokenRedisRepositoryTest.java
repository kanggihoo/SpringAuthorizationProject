package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.example.config.RedisConfig;

/**
 * TokenRedisRepository Testcontainers 통합 테스트.
 *
 * <p>
 * 실제 Redis 컨테이너를 띄워 RT 저장·조회·삭제와 AT Blacklist 등록·조회를 검증한다.
 */
@DataRedisTest
@Testcontainers
@ActiveProfiles("test")
@Import({ TokenRedisRepository.class, RedisConfig.class })
class TokenRedisRepositoryTest {

    private static final String RT_PREFIX = "RT:";
    private static final String BL_PREFIX = "BL:";

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
        // 저장소가 사용하는 키만 정리해 다른 테스트와의 간섭 범위를 줄인다.
        deleteKeysByPrefix(RT_PREFIX);
        deleteKeysByPrefix(BL_PREFIX);
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
    @DisplayName("saveRefreshToken 저장 시 Redis에 초 단위 TTL이 설정된다")
    void saveRefreshToken_setsTtl() {
        // given
        String username = "ttluser";
        long ttlSeconds = 5L;

        // when
        tokenRedisRepository.saveRefreshToken(username, "token-value", ttlSeconds);

        // then
        Long ttl = stringRedisTemplate.getExpire(RT_PREFIX + username, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, ttlSeconds);
    }

    @Test
    @DisplayName("같은 username으로 saveRefreshToken을 다시 호출하면 최신 RT로 덮어쓴다")
    void saveRefreshToken_overwritesExistingToken() {
        // given
        String username = "overwrite-user";
        tokenRedisRepository.saveRefreshToken(username, "old-token", 30L);

        // when
        tokenRedisRepository.saveRefreshToken(username, "new-token", 30L);

        // then
        assertThat(tokenRedisRepository.findRefreshToken(username))
                .isPresent()
                .hasValue("new-token");
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

        Long ttl = stringRedisTemplate.getExpire(BL_PREFIX + accessToken, TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, remainingTtlMillis);
    }

    @Test
    @DisplayName("Blacklist에 없는 토큰에 대해 isBlacklisted는 false를 반환한다")
    void isBlacklisted_returnsFalse_forUnregisteredToken() {
        // given
        String accessToken = "not-blacklisted-token";

        // when & then
        assertThat(tokenRedisRepository.isBlacklisted(accessToken)).isFalse();
    }

    @Test
    @DisplayName("짧은 TTL로 등록한 블랙리스트 토큰은 만료 후 자동 제거된다")
    void addToBlacklist_expiresAfterTtl() throws InterruptedException {
        // given
        String accessToken = "expiring-access-token";
        long remainingTtlMillis = 150L;

        // when
        tokenRedisRepository.addToBlacklist(accessToken, remainingTtlMillis);

        // then
        waitUntilKeyExpires(BL_PREFIX + accessToken, Duration.ofSeconds(2));
        assertThat(tokenRedisRepository.isBlacklisted(accessToken)).isFalse();
    }

    private void deleteKeysByPrefix(String prefix) {
        var keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private void waitUntilKeyExpires(String key, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
                return;
            }
            Thread.sleep(25L);
        }
        assertThat(stringRedisTemplate.hasKey(key)).isFalse();
    }
}
