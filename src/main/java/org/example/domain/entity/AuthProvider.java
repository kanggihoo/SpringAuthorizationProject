package org.example.domain.entity;

/**
 * 사용자 인증 제공자 종류를 정의하는 열거형.
 *
 * <p>String 타입 대신 Enum을 사용함으로써 대소문자 불일치 등의 휴먼 에러를 방지하고,
 * 컴파일 타임에 유효하지 않은 제공자 값을 감지할 수 있다.
 *
 * <p>새로운 소셜 로그인 제공자를 추가할 때는 이 Enum에 값을 추가하고,
 * {@link org.example.security.oauth2.OAuth2UserInfoFactory}에 case를 추가하면 된다.
 */
public enum AuthProvider {
    /** 일반 이메일/비밀번호 기반 자체 로그인 */
    LOCAL,

    /** Google OAuth2 소셜 로그인 */
    GOOGLE

    // 향후 추가 예정:
    // GITHUB,
    // KAKAO,
    // NAVER
}
