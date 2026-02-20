package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 프로젝트 메인 실행 클래스
 * org.example 패키지에 위치하여 하위 패키지(domain, repository, service 등)를 자동으로 스캔합니다.
 */
@SpringBootApplication
public class JavaMiniProjectApplication {

  public static void main(String[] args) {
    SpringApplication.run(JavaMiniProjectApplication.class, args);
  }

}
