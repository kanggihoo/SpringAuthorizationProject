package org.example.controller;

import org.example.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 권한 테스트를 위한 컨트롤러
 */
@RestController
public class TestController {

  /**
   * 누구나 접근 가능한 메인 페이지
   */
  @GetMapping("/")
  public String index(@AuthenticationPrincipal CustomUserDetails userDetails) {
    if (userDetails != null) {
      return "안녕하세요, " + userDetails.getNickname() + "님! (현재 권한: " + userDetails.getAuthorities() + ")";
    }
    return "메인 페이지입니다. 로그인이 필요합니다.";
  }

  /**
   * USER 권한 이상만 접근 가능
   */
  @GetMapping("/user/profile")
  public String userProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return "회원 프로필 페이지입니다. 반가워요, " + userDetails.getNickname() + "님!";
  }

  /**
   * ADMIN 권한만 접근 가능
   */
  @GetMapping("/admin/manage")
  public String adminManage() {
    return "관리자 전용 페이지입니다.";
  }
}
