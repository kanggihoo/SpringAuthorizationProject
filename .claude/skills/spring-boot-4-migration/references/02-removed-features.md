# 제거된 기능 및 대체재

Spring Boot 4.0에서 완전히 제거된 기능들. 해당 기능을 사용 중이라면 반드시 대체재로 전환해야 한다.

---

## Undertow 지원 제거

### 이유
Undertow가 **Servlet 6.1**과 호환되지 않아 제거됨.

### 영향
```xml
<!-- 더 이상 동작하지 않음 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
  <exclusions>
    <exclusion>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-tomcat</artifactId>
    </exclusion>
  </exclusions>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-undertow</artifactId> <!-- 존재하지 않음 -->
</dependency>
```

### 대체재: Tomcat (기본값)

```xml
<!-- 기본값 — 별도 설정 불필요 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 대체재: Jetty

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
  <exclusions>
    <exclusion>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-tomcat</artifactId>
    </exclusion>
  </exclusions>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

> **선택 기준**: Undertow의 주요 장점은 낮은 메모리 사용량과 Non-blocking I/O였다.
> WebFlux(Reactive) 스택을 사용한다면 Netty가 동일한 이점을 제공한다.
> 서블릿 기반이라면 Jetty가 가장 유사한 대안이다.

---

## Pulsar Reactive 제거

### 이유
Spring Pulsar 팀이 Reactor 지원을 공식 중단함에 따라 auto-configuration 제거.

### 영향
- `spring-boot-starter-pulsar-reactive` 스타터 없음
- `PulsarReactiveAutoConfiguration` 없음

### 대체재
- Imperative(동기) 방식: `spring-boot-starter-pulsar` 사용
- Reactive 유지 필요 시: Spring Pulsar 팀의 별도 모듈 확인 (커뮤니티 지원)

```xml
<!-- Reactive 대신 Imperative Pulsar -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-pulsar</artifactId>
</dependency>
```

---

## Embedded Launch Scripts 제거

### 이유
"Fully executable" jar (OS init.d/systemd 직접 실행) 지원 종료.

### 영향
```xml
<!-- Maven: 더 이상 동작하지 않음 -->
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <executable>true</executable> <!-- 이 옵션 제거됨 -->
  </configuration>
</plugin>
```

### 대체재
표준 `java -jar` 방식은 여전히 동작한다.

```bash
java -jar myapp.jar
```

systemd 서비스로 등록하려면 unit 파일을 직접 작성하라:

```ini
# /etc/systemd/system/myapp.service
[Unit]
Description=My Spring Boot App

[Service]
ExecStart=/usr/bin/java -jar /opt/myapp/myapp.jar
User=myapp

[Install]
WantedBy=multi-user.target
```

---

## Session 구현체 — Hazelcast / MongoDB

### 변경사항
Spring Session의 Hazelcast, MongoDB 구현체가 각 팀(Hazelcast, MongoDB)으로 이전됨.
Spring Boot의 auto-configuration에서 제거.

### 대체재
각 팀이 제공하는 스타터/모듈을 직접 추가해야 한다. 각 팀의 최신 문서 참고.

---

## Spock 통합 제거

### 이유
Spock 프레임워크가 Groovy 5와 호환되지 않아 제거.

### 영향
- `@SpringBootTest`와 함께 Spock 사용 시 자동 구성 불가

### 대체재
- **JUnit 5** (공식 권장)
- Spock 팀의 별도 Spring Boot 4 호환 버전 출시 시까지 JUnit 5로 전환

```xml
<!-- JUnit 5는 spring-boot-starter-test에 기본 포함 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

---

## 제거된 기능 빠른 참조

| 제거된 기능 | 대체재 |
|---|---|
| `spring-boot-starter-undertow` | `spring-boot-starter-tomcat` (기본) 또는 `spring-boot-starter-jetty` |
| `spring-boot-starter-pulsar-reactive` | `spring-boot-starter-pulsar` (Imperative) |
| Maven `<executable>true</executable>` | `java -jar` + systemd unit 파일 |
| Spring Session Hazelcast/MongoDB auto-config | 각 팀 제공 모듈 |
| Spock Spring 통합 | JUnit 5 (`spring-boot-starter-test` 기본 포함) |
