package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 연결 및 템플릿 설정.
 *
 * <p>
 * AT Blacklist와 RT 저장에 사용할 {@link StringRedisTemplate}을 빈으로 등록한다.
 * 키·값 모두 String 타입으로 직렬화하여 redis-cli로 직접 확인 가능하게 한다.
 */
@Configuration
public class RedisConfig {

    /**
     * Redis 문자열 작업용 템플릿 빈.
     *
     * @param connectionFactory Spring Boot 자동 설정으로 주입되는 연결 팩토리
     * @return StringRedisTemplate 인스턴스
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
