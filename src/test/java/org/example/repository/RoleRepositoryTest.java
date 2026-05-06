package org.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.domain.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("findByName은 권한명을 기준으로 Role을 조회한다")
    void findByName_returnsRole() {
        // given
        roleRepository.save(new Role("ROLE_ADMIN"));

        // when
        var result = roleRepository.findByName("ROLE_ADMIN");

        // then
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("존재하지 않는 권한명으로 조회하면 Optional.empty()를 반환한다")
    void findByName_returnsEmpty_whenRoleDoesNotExist() {
        // when
        var result = roleRepository.findByName("ROLE_UNKNOWN");

        // then
        assertThat(result).isEmpty();
    }
}
