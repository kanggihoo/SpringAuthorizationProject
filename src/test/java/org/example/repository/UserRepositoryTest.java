package org.example.repository;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("UserRepository — DataJpa DB 슬라이스 테스트")
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findByUsername: 존재하는 username으로 조회 시 User 객체를 반환한다")
    void findByUsername_returnsUser_whenExists() {
        // given
        User user = User.builder()
                .username("testuser")
                .password("password")
                .nickname("TestNick")
                .build();
        entityManager.persistAndFlush(user);

        // when
        Optional<User> found = userRepository.findByUsername("testuser");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("findByUsername: 존재하지 않는 username으로 조회 시 빈 Optional을 반환한다")
    void findByUsername_returnsEmpty_whenNotExists() {
        // when
        Optional<User> found = userRepository.findByUsername("unknown");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByUsername: 삭제된 유저를 조회하면 빈 Optional을 반환한다")
    void findByUsername_returnsEmpty_afterDeleted() {
        // given
        User user = User.builder()
                .username("tobe_deleted")
                .password("password")
                .nickname("Nick")
                .build();
        User savedUser = entityManager.persistAndFlush(user);

        // when
        userRepository.delete(savedUser);
        entityManager.flush();
        entityManager.clear();

        Optional<User> found = userRepository.findByUsername("tobe_deleted");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save: User 저장 시 ID가 자동 생성되고 명시된 Role들이 함께 영속화된다")
    void save_generatesIdAndPersistsRoles() {
        // given
        Role role = new Role("ROLE_USER");
        entityManager.persistAndFlush(role);

        User user = User.builder()
                .username("testuser_with_roles")
                .password("password")
                .nickname("RoleTester")
                .build();
        user.addRole(role);

        // when
        User savedUser = userRepository.save(user);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 지우고 DB에서 확실히 조회

        // then
        assertThat(savedUser.getId()).isNotNull();

        User retrievedUser = entityManager.find(User.class, savedUser.getId());
        // User 객체의 FetchType.EAGER 설정에 의해 즉시 로딩됨을 확인
        assertThat(retrievedUser.getRoles()).hasSize(1);
        assertThat(retrievedUser.getRoles().iterator().next().getName()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("save: 중복된 username으로 저장 시 DataIntegrityViolationException 예외가 발생한다")
    void save_enforcesUniqueUsername() {
        // given
        User firstUser = User.builder()
                .username("duplicate_user")
                .password("pass1")
                .nickname("Nick1")
                .build();
        entityManager.persistAndFlush(firstUser);

        User secondUser = User.builder()
                .username("duplicate_user") // 중복된 이름
                .password("pass2")
                .nickname("Nick2")
                .build();

        // when & then
        assertThatThrownBy(() -> {
            userRepository.save(secondUser);
            entityManager.flush(); // 제약조건은 DB 삽입 쿼리 전송 시 발생하므로 flush 필요
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
