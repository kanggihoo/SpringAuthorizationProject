package org.example.security.account;

/**
 * 로그인 실패 임계치 도달 시 User 상태를 잠그는 서비스.
 */
public interface AccountLockService {

  void lockForLoginFailure(String username);
}

