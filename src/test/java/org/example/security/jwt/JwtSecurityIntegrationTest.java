package org.example.security.jwt;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.controller.TestController;
import org.example.domain.entity.Role;
import org.example.domain.entity.User;
import org.example.exception.GlobalExceptionHandler;
import org.example.security.CustomUserDetails;
import org.example.security.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(TestController.class)
@Import({JwtSecurityTestConfig.class, GlobalExceptionHandler.class})
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Allows a valid USER token to access a protected user endpoint")
    void returnsOk_whenUserTokenAccessesUserEndpoint() throws Exception {
        given(customUserDetailsService.loadUserByUsername("testuser"))
            .willReturn(createUserDetails("testuser", "tester", "ROLE_USER"));

        performAuthorizedGet("/user/profile", "testuser", "ROLE_USER")
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("tester")));
    }

    @Test
    @DisplayName("Returns 401 for a protected user endpoint when the token is missing")
    void returnsUnauthorized_whenTokenMissing() throws Exception {
        mockMvc.perform(get("/user/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Returns 403 when a USER token accesses an admin endpoint")
    void returnsForbidden_whenUserTokenAccessesAdminEndpoint() throws Exception {
        given(customUserDetailsService.loadUserByUsername("testuser"))
            .willReturn(createUserDetails("testuser", "tester", "ROLE_USER"));

        performAuthorizedGet("/admin/manage", "testuser", "ROLE_USER")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Allows an ADMIN token to access an admin endpoint")
    void returnsOk_whenAdminTokenAccessesAdminEndpoint() throws Exception {
        given(customUserDetailsService.loadUserByUsername("admin"))
            .willReturn(createUserDetails("admin", "admin-user", "ROLE_ADMIN"));

        performAuthorizedGet("/admin/manage", "admin", "ROLE_ADMIN")
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Returns 401 through the exception handler filter for a tampered token")
    void returnsUnauthorized_whenTokenIsTampered() throws Exception {
        String token = bearerToken("testuser", "ROLE_USER") + "tampered";

        mockMvc.perform(get("/user/profile")
                .header("Authorization", token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid Token"));
    }

    @Test
    @DisplayName("Allows public endpoints without a JWT")
    void returnsOk_whenAccessingPublicEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk());
    }

    private String bearerToken(String username, String... roles) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(username, List.of(roles));
    }

    private ResultActions performAuthorizedGet(String uri, String username, String... roles) throws Exception {
        return mockMvc.perform(get(uri).header("Authorization", bearerToken(username, roles)));
    }

    private CustomUserDetails createUserDetails(String username, String nickname, String... roles) {
        User user = User.builder()
            .username(username)
            .password("encoded-password")
            .nickname(nickname)
            .build();
        for (String roleName : roles) {
            user.addRole(new Role(roleName));
        }
        return new CustomUserDetails(user);
    }
}
