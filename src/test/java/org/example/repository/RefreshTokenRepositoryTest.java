package org.example.repository;

import org.example.TestFixtures;
import org.example.domain.entity.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("존재하는 userId로 조회 시 RefreshToken을 반환한다")
    void findByUserId_returnsToken_whenExists() {
        // given
        RefreshToken token = TestFixtures.buildRefreshToken(null, 100L, "valid-token");
        entityManager.persistAndFlush(token);
        entityManager.clear();

        // when
        Optional<RefreshToken> result = refreshTokenRepository.findByUserId(100L);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getRefreshToken()).isEqualTo("valid-token");
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회 시 빈 Optional을 반환한다")
    void findByUserId_returnsEmpty_whenNotFound() {
        // when
        Optional<RefreshToken> result = refreshTokenRepository.findByUserId(999L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하는 토큰 문자열로 조회 시 RefreshToken을 반환한다")
    void findByRefreshToken_returnsToken_whenExists() {
        // given
        RefreshToken token = TestFixtures.buildRefreshToken(null, 101L, "existing-token");
        entityManager.persistAndFlush(token);
        entityManager.clear();

        // when
        Optional<RefreshToken> result = refreshTokenRepository.findByRefreshToken("existing-token");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("존재하지 않는 토큰 문자열로 조회 시 빈 Optional을 반환한다")
    void findByRefreshToken_returnsEmpty_forUnknownToken() {
        // when
        Optional<RefreshToken> result = refreshTokenRepository.findByRefreshToken("unknown-token");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("userId로 RefreshToken을 삭제하면 이후 조회되지 않는다")
    void deleteByUserId_removesToken() {
        // given
        RefreshToken token = TestFixtures.buildRefreshToken(null, 102L, "to-delete-token");
        entityManager.persistAndFlush(token);
        entityManager.clear();

        // when
        refreshTokenRepository.deleteByUserId(102L);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<RefreshToken> result = refreshTokenRepository.findByUserId(102L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("이미 존재하는 userId로 RefreshToken 저장 시 DataIntegrityViolationException 예외가 발생한다")
    void save_enforcesUniqueUserId() {
        // given
        RefreshToken token1 = TestFixtures.buildRefreshToken(null, 103L, "token-1");
        entityManager.persistAndFlush(token1);

        // when & then
        RefreshToken token2 = TestFixtures.buildRefreshToken(null, 103L, "token-2");
        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(token2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RTR 갱신: 토큰 값과 만료일을 변경하면 저장소에 반영된다")
    void updateToken_changesValueAndExpiry() {
        // given
        RefreshToken token = TestFixtures.buildRefreshToken(null, 104L, "old-token");
        entityManager.persistAndFlush(token);
        entityManager.clear();

        // when
        RefreshToken savedToken = refreshTokenRepository.findByUserId(104L).orElseThrow();
        // PostgreSQL timestamp precision may vary, truncate to MICROS for exact match
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(14).truncatedTo(ChronoUnit.MICROS);
        savedToken.updateToken("new-token", newExpiry);
        refreshTokenRepository.saveAndFlush(savedToken);
        entityManager.clear();

        // then
        Optional<RefreshToken> updatedToken = refreshTokenRepository.findByUserId(104L);
        assertThat(updatedToken).isPresent();
        assertThat(updatedToken.get().getRefreshToken()).isEqualTo("new-token");
        assertThat(updatedToken.get().getExpiryDate()).isEqualTo(newExpiry);
    }
}
