package org.example.repository;

import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================
 * Phase 1: 데이터 접근 계층 (Repository) 테스트
 * ============================================================
 *
 * [@DataJpaTest]
 *   - JPA 관련 컴포넌트(Entity, Repository)만 로드하는 "슬라이스 테스트" 어노테이션
 *   - 전체 애플리케이션 컨텍스트를 띄우지 않아 빠르게 실행됨
 *   - 내부적으로 @Transactional이 포함되어 있어 각 테스트 후 자동 롤백됨
 *   - 기본적으로 내장 DB(H2)를 사용하려고 시도함
 *
 * [@ActiveProfiles("test")]
 *   - application-test.yml 설정을 활성화하여 H2 인메모리 DB 사용
 *   - 운영 DB(PostgreSQL)와 완벽히 격리된 환경에서 테스트 실행
 *
 * [테스트 전략]
 *   - Role 저장 → User 생성 시 Role 매핑 → User 저장 → findByUsername() 조회
 *   - 이 한 번의 흐름으로 엔티티 저장, 연관관계 매핑(user_roles), 조회를 통합 검증
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    // @DataJpaTest가 자동으로 빈을 등록해주므로 @Autowired로 주입 가능
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Role 저장 → User 생성 및 Role 매핑 → 조회 시 데이터와 연관관계 정상 유지")
    void saveUserWithRoleAndFindByUsername() {
        // ============================================================
        // [Given] 테스트 데이터 준비 단계
        // ============================================================

        // 1. Role 엔티티를 먼저 저장 (User가 참조할 권한 데이터)
        //    - JPA의 영속성 컨텍스트에 의해 save() 후 ID가 자동 할당됨
        Role role = new Role("ROLE_USER");
        Role savedRole = roleRepository.save(role);

        // 2. User 엔티티를 빌더 패턴으로 생성
        User user = User.builder()
                .username("testuser")
                .password("encodedPassword123")
                .nickname("Tester")
                .build();

        // 3. User에 Role을 매핑 (N:M 관계 - user_roles 교차 테이블에 레코드 생성)
        user.addRole(savedRole);

        // 4. User 저장 - cascade 설정 없이도 이미 영속화된 Role과 매핑됨
        userRepository.save(user);

        // ============================================================
        // [When] 실제 테스트할 동작 실행
        // ============================================================

        // findByUsername()으로 방금 저장한 User를 DB에서 조회
        Optional<User> foundUser = userRepository.findByUsername("testuser");

        // ============================================================
        // [Then] 결과 검증 단계
        // ============================================================

        // AssertJ의 assertThat()을 사용한 가독성 높은 검증
        // isPresent() : Optional에 값이 존재하는지 확인
        assertThat(foundUser).isPresent();

        User result = foundUser.get();

        // 기본 필드 검증
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isEqualTo("encodedPassword123");
        assertThat(result.getNickname()).isEqualTo("Tester");

        // 계정 상태 기본값 검증 (User 엔티티의 기본값이 true인지)
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();

        // ★ 핵심: N:M 연관관계(user_roles 교차 테이블) 매핑이 정상 유지되는지 검증
        // - roles 컬렉션이 비어있지 않고, 정확히 1개의 Role을 포함하는지
        // - 그 Role의 이름이 "ROLE_USER"인지
        assertThat(result.getRoles()).hasSize(1);
        assertThat(result.getRoles())
                .extracting("name")  // Role 객체들에서 name 필드만 추출
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("존재하지 않는 username으로 조회 시 빈 Optional 반환")
    void findByUsername_NotFound_ReturnsEmpty() {
        // ============================================================
        // [When] DB에 저장하지 않은 username으로 조회 시도
        // ============================================================
        Optional<User> result = userRepository.findByUsername("nonexistent");

        // ============================================================
        // [Then] Optional.empty()가 반환되는지 확인
        // - 이 동작은 CustomUserDetailsService에서 UsernameNotFoundException을
        //   발생시키는 근거가 됨
        // ============================================================
        assertThat(result).isEmpty();
    }
}
