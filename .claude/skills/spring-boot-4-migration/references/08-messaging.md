# 메시지 큐 변경 (Kafka / AMQP)

Kafka Streams와 AMQP(RabbitMQ)의 retry 관련 주요 변경사항.

---

## Kafka Streams — StreamsBuilderFactoryBeanConfigurer

### 변경사항
`StreamBuilderFactoryBeanCustomizer`가 Spring Kafka 네이티브 인터페이스인
`StreamsBuilderFactoryBeanConfigurer`로 교체됐다.

### Before / After

```java
// 이전
import org.springframework.boot.autoconfigure.kafka.StreamBuilderFactoryBeanCustomizer;
import org.apache.kafka.streams.StreamsBuilder;

@Bean
public StreamBuilderFactoryBeanCustomizer customizer() {
    return factoryBean -> {
        factoryBean.setAutoStartup(false);
        factoryBean.setStateListener((newState, oldState) -> {
            log.info("Kafka Streams state: {} -> {}", oldState, newState);
        });
    };
}
```

```java
// 이후
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

@Bean
public StreamsBuilderFactoryBeanConfigurer configurer() {
    return factoryBean -> {
        factoryBean.setAutoStartup(false);
        factoryBean.setStateListener((newState, oldState) -> {
            log.info("Kafka Streams state: {} -> {}", oldState, newState);
        });
    };
}
```

### 의존성 확인

`StreamsBuilderFactoryBeanConfigurer`는 Spring Kafka에서 제공한다.
`spring-boot-starter-kafka`에 포함되어 있다.

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

---

## Kafka Retry — Spring Framework retry 패키지로 이전

### 변경사항
Kafka retry 기능이 Spring Retry 라이브러리에서 Spring Framework의 retry 패키지로 이전됐다.

### 영향 받는 클래스들

```java
// 이전 import (Spring Retry)
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

// 이후 import (Spring Framework retry)
import org.springframework.core.retry.backoff.FixedBackOffPolicy;
import org.springframework.core.retry.policy.SimpleRetryPolicy;
import org.springframework.core.retry.support.RetryTemplate;
```

### Kafka Listener 재시도 설정 변경

```java
// 이전
@Bean
public DefaultErrorHandler errorHandler() {
    FixedBackOffPolicy backOff = new FixedBackOffPolicy();
    backOff.setBackOffPeriod(1000L);
    return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
}
```

```java
// 이후 — Spring Framework retry 클래스 사용
@Bean
public DefaultErrorHandler errorHandler() {
    return new DefaultErrorHandler(
        new org.springframework.core.retry.backoff.FixedBackOff(1000L, 3L)
    );
}
```

---

## AMQP (RabbitMQ) Retry — Spring Framework retry 패키지로 이전

### 변경사항
AMQP retry도 Kafka와 동일하게 Spring Retry → Spring Framework retry로 이전됐다.

### RabbitMQ Listener 재시도 설정

```java
// 이전
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.support.RetryTemplate;

@Bean
public RetryOperationsInterceptor retryInterceptor() {
    return RetryInterceptorBuilder.stateless()
        .maxAttempts(3)
        .backOffOptions(1000, 2.0, 10000)
        .build();
}
```

```java
// 이후
import org.springframework.core.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.core.retry.support.RetryTemplate;

@Bean
public RetryOperationsInterceptor retryInterceptor() {
    return RetryInterceptorBuilder.stateless()
        .maxAttempts(3)
        .backOffOptions(1000, 2.0, 10000)
        .build();
}
```

### 프로퍼티 방식 재시도 설정 (변경 없음)

프로퍼티 파일을 통한 설정은 변경이 없다:

```properties
# Kafka retry (변경 없음)
spring.kafka.listener.retry.max-attempts=3
spring.kafka.listener.retry.backoff.delay=1000ms

# RabbitMQ retry (변경 없음)
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.retry.initial-interval=1000ms
spring.rabbitmq.listener.simple.retry.multiplier=2.0
spring.rabbitmq.listener.simple.retry.max-interval=10000ms
```

> **프로퍼티 기반 설정을 사용한다면 코드 변경이 불필요하다.**
> Java 코드로 직접 `RetryTemplate`이나 retry 관련 클래스를 import하는 경우에만 변경 필요.

---

## 체크리스트

### Kafka Streams
- [ ] `StreamBuilderFactoryBeanCustomizer` → `StreamsBuilderFactoryBeanConfigurer`
- [ ] import 경로: `org.springframework.boot.autoconfigure.kafka` → `org.springframework.kafka.config`

### Kafka Retry
- [ ] `org.springframework.retry.*` import → `org.springframework.core.retry.*`
- [ ] 프로퍼티 기반 설정이라면 변경 불필요

### AMQP Retry
- [ ] `org.springframework.retry.*` import → `org.springframework.core.retry.*`
- [ ] 프로퍼티 기반 설정이라면 변경 불필요
