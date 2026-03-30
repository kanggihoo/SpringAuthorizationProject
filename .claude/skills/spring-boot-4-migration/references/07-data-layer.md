# 데이터 레이어 변경

Elasticsearch, MongoDB, Spring Batch의 주요 변경사항.

---

## Elasticsearch — Rest5Client

### 변경사항
저수준 HTTP 클라이언트가 `RestClient` → `Rest5Client`로 교체됐다.
Elasticsearch 8.x의 새로운 Java client 기반.

### 의존성 변경

```xml
<!-- 이전 -->
<dependency>
  <groupId>org.elasticsearch.client</groupId>
  <artifactId>elasticsearch-rest-client</artifactId>
</dependency>

<!-- 이후 (spring-boot-starter-data-elasticsearch에 자동 포함됨) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

### 커스터마이저 변경

```java
// 이전
@Bean
public RestClientBuilderCustomizer myElasticsearchCustomizer() {
    return builder -> {
        builder.setRequestConfigCallback(config ->
            config.setConnectTimeout(5000)
                  .setSocketTimeout(60000));
    };
}
```

```java
// 이후
@Bean
public Rest5ClientBuilderCustomizer myElasticsearchCustomizer() {
    return builder -> {
        builder.setRequestConfigCallback(config ->
            config.setConnectTimeout(5000)
                  .setResponseTimeout(60000));
    };
}
```

### import 변경

```java
// 이전
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;

// 이후
import co.elastic.clients.transport.rest5_client.Rest5Client;
import org.springframework.boot.autoconfigure.elasticsearch.Rest5ClientBuilderCustomizer;
```

---

## MongoDB — 설정 프로퍼티 재구성

### 프로퍼티 네임스페이스 변경

가장 많은 영향을 주는 변경. `spring.data.mongodb` → `spring.mongodb`로 이동.

```properties
# 이전
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=mydb
spring.data.mongodb.username=admin
spring.data.mongodb.password=secret
spring.data.mongodb.authentication-database=admin

# 이후
spring.mongodb.host=localhost
spring.mongodb.port=27017
spring.mongodb.database=mydb
spring.mongodb.username=admin
spring.mongodb.password=secret
spring.mongodb.authentication-database=admin
```

URI 방식 (권장):
```properties
# 이전
spring.data.mongodb.uri=mongodb://admin:secret@localhost:27017/mydb

# 이후
spring.mongodb.uri=mongodb://admin:secret@localhost:27017/mydb
```

### UUID 표현 방식 — 명시적 설정 필요

이전에는 Binary UUID가 기본값이었으나, 이제 명시적 설정이 필요하다.

```properties
# 표준 UUID (string 형태로 저장) — 신규 프로젝트 권장
spring.mongodb.uuid-representation=standard

# Java UUID (Binary 형태) — 기존 데이터와의 호환성 유지 시
spring.mongodb.uuid-representation=java-legacy

# C# Legacy UUID — C# 클라이언트와 호환 필요 시
spring.mongodb.uuid-representation=c-sharp-legacy
```

### BigDecimal 표현 방식

```properties
# Decimal128으로 MongoDB에 정확한 소수 저장
spring.mongodb.decimal128-representation=decimal128

# String으로 저장 (기본값 — 이전 동작과 동일)
spring.mongodb.decimal128-representation=string
```

### Auto-configuration 주의사항
- Hazelcast / Redis Session 구현체는 `02-removed-features.md` 참고
- MongoDB Session은 각 팀으로 이전됨

---

## Spring Batch — 기본 동작 변경

### 변경사항
Spring Boot 4.0에서 Spring Batch의 기본 동작이 **in-memory**로 변경됐다.
이전에는 classpath에 JDBC가 있으면 자동으로 DB 기반으로 동작했다.

### DB 기반 메타데이터 저장 (이전 동작 복원)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-batch-jdbc</artifactId>
</dependency>
```

```properties
# DB 기반 배치 메타데이터 저장 활성화
spring.batch.jdbc.initialize-schema=always
```

### In-memory 모드 (새로운 기본값)

```xml
<!-- spring-boot-starter-batch만 있으면 in-memory로 동작 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-batch</artifactId>
</dependency>
```

> **운영 환경 주의**: In-memory 배치는 서버 재시작 시 Job 실행 이력이 사라진다.
> 재시작 가능한 Job, 중복 실행 방지가 필요하다면 반드시 `spring-boot-starter-batch-jdbc`를 추가하라.

### JobRepository 설정 변경

```java
// 이전 — JobRepository가 자동으로 DB 기반으로 생성됐음
// 별도 설정 불필요

// 이후 — in-memory 명시적 사용
@Bean
public JobRepository jobRepository() {
    return new ResourcelessJobRepository(); // in-memory
}
```

---

## 체크리스트

### Elasticsearch
- [ ] `RestClientBuilderCustomizer` → `Rest5ClientBuilderCustomizer`
- [ ] `RestClient` import → `Rest5Client`

### MongoDB
- [ ] `spring.data.mongodb.*` → `spring.mongodb.*` (모든 프로퍼티)
- [ ] `spring.mongodb.uuid-representation` 명시적 설정
- [ ] 기존 UUID 데이터와 호환성 확인

### Spring Batch
- [ ] DB 기반 배치 필요 시 `spring-boot-starter-batch-jdbc` 추가
- [ ] `spring.batch.jdbc.initialize-schema` 설정 확인
- [ ] In-memory 모드 사용 시 운영 환경 재시작 이력 손실 허용 여부 검토
