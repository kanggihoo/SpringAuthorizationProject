package org.example;

import org.example.domain.entity.RefreshToken;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.security.CustomUserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

public class TestFixtures {

    public static User buildUser(Long id, String username, String nickname, String roleName) {
        User user = User.builder()
                .username(username)
                .password("encoded_password")
                .nickname(nickname)
                .build();
        
        if (id != null) {
            ReflectionTestUtils.setField(user, "id", id);
        }
        
        if (roleName != null) {
            Role role = new Role(roleName);
            // Default Role ID to avoid null pointer when mocking or comparing
            ReflectionTestUtils.setField(role, "id", 1L); 
            user.addRole(role);
        }
        
        return user;
    }

    public static RefreshToken buildRefreshToken(Long id, Long userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .refreshToken(token)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
                
        if (id != null) {
            ReflectionTestUtils.setField(refreshToken, "id", id);
        }
        
        return refreshToken;
    }

    public static CustomUserDetails buildUserDetails(Long userId, String username, String roleName) {
        User user = buildUser(userId, username, "TestNick", roleName);
        return new CustomUserDetails(user);
    }
}
