## Logout에서  @AuthenticationPrincipal 사용하는 경우 (Oauth2 , 일반 로그인)
    -- @AuthenticationPrincipal CustomUserDetails userDetails 로 사용하려면
    -- 일반 로그인: 
    -- CustomUserDetails가 UserDetails를 구현해야한다
    -- Oauth2 로그인:
    -- CustomUserDetails가 OAuth2User를 구현해야한다

    

@PostMapping("/logout")
  public ResponseEntity<String> logout(
      @AuthenticationPrincipal CustomUserDetails userDetails,

1. (가장 추천) CustomUserDetails를 만능 객체로 만들기
가장 우아하고 많이 쓰는 방법입니다. CustomUserDetails가 일반 유저용 인터페이스(UserDetails)와 소셜 유저용 인터페이스(OAuth2User)를 둘 다 구현(implements) 하도록 만드는 것입니다.

java
public class CustomUserDetails implements UserDetails, OAuth2User {
    private final User user;
    private Map<String, Object> attributes; // 소셜 로그인 정보 담는 곳
    // 일반 로그인용 생성자
    public CustomUserDetails(User user) { ... }
    
    // 소셜 로그인용 생성자
    public CustomUserDetails(User user, Map<String, Object> attributes) { ... }
    
    // ... UserDetails, OAuth2User 메서드들 오버라이딩
}
이렇게 하면 폼 로그인이든 소셜 로그인이든 SecurityContext에는 무조건 CustomUserDetails 타입이 들어가게 되므로, 컨트롤러에서 지금처럼 @AuthenticationPrincipal CustomUserDetails userDetails로 안전하게 받을 수 있습니다.