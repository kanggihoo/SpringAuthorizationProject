package org.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationFailureHandlerTest {

    @Mock
    private CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @InjectMocks
    private OAuth2AuthenticationFailureHandler failureHandler;

    @Test
    @DisplayName("Clears oauth cookie and redirects with an encoded error message on failure")
    void onAuthenticationFailure_clearsCookieAndRedirectsWithEncodedError() throws Exception {
        ReflectionTestUtils.setField(failureHandler, "redirectUri", "http://localhost:3000/oauth2/callback");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
            new OAuth2Error("invalid_request"),
            "OAuth2 login failed"
        );

        failureHandler.onAuthenticationFailure(request, response, exception);

        verify(cookieAuthorizationRequestRepository).deleteCookie(
            request,
            response,
            CookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME
        );
        assertThat(response.getRedirectedUrl()).isEqualTo(
            "http://localhost:3000/oauth2/callback?error="
                + URLEncoder.encode("OAuth2 login failed", StandardCharsets.UTF_8)
        );
    }
}
