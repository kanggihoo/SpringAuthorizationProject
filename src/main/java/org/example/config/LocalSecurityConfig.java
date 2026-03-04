package org.example.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 로컬 환경 전용 설정 (H2 콘솔 서블릿 등록 및 Security 설정)
 */
@Configuration
@Profile("local")
public class LocalSecurityConfig {

  @Bean
  ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {
    ServletRegistrationBean<JakartaWebServlet> registration =
        new ServletRegistrationBean<>(new JakartaWebServlet());
    registration.addUrlMappings("/h2-console/*");
    registration.setName("H2Console");
    registration.setLoadOnStartup(1);
    return registration;
  }

  @Bean
  @Order(1)
  SecurityFilterChain h2SecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/h2-console/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .headers(headers -> headers.frameOptions(options -> options.sameOrigin()));
    return http.build();
  }

}
