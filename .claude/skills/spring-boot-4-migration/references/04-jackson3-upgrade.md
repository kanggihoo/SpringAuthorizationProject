# Jackson 3 업그레이드

Spring Boot 4.0은 기본적으로 **Jackson 3**을 사용한다.
Jackson 3은 Group ID가 변경되는 등 Jackson 2와 여러 Breaking Change가 있다.

---

## Group ID 변경

가장 핵심적인 변경. 모든 Jackson 의존성의 Group ID가 바뀐다.

| 버전 | Group ID |
|---|---|
| Jackson 2 | `com.fasterxml.jackson` |
| Jackson 3 | `tools.jackson` |

### Maven 의존성 변경 예시

```xml
<!-- 이전 (Jackson 2) -->
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
  <groupId>com.fasterxml.jackson.datatype</groupId>
  <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>

<!-- 이후 (Jackson 3) - Spring Boot 4.0 BOM이 버전 관리 -->
<dependency>
  <groupId>tools.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
  <groupId>tools.jackson.datatype</groupId>
  <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

> Spring Boot 4.0의 BOM(Bill of Materials)을 사용하면 버전은 자동 관리된다.
> `spring-boot-starter-web`이나 `spring-boot-starter-json`을 통해 자동 포함된다.

---

## 클래스 이름 변경

| 이전 (Jackson 2) | 이후 (Jackson 3) |
|---|---|
| `JsonObjectSerializer` | `ObjectValueSerializer` |
| `Jackson2ObjectMapperBuilderCustomizer` | `JsonMapperBuilderCustomizer` |
| `@JsonComponent` | `@JacksonComponent` |

### ObjectMapper 커스터마이징 변경

```java
// 이전
@Bean
public Jackson2ObjectMapperBuilderCustomizer customizer() {
    return builder -> builder
        .featuresToEnable(SerializationFeature.INDENT_OUTPUT)
        .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
}

// 이후
@Bean
public JsonMapperBuilderCustomizer customizer() {
    return builder -> builder
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
}
```

### @JacksonComponent 변경

```java
// 이전
@JsonComponent
public class UserSerializer extends JsonSerializer<User> {
    @Override
    public void serialize(User user, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("name", user.getName());
        gen.writeEndObject();
    }
}

// 이후
@JacksonComponent
public class UserSerializer extends JsonSerializer<User> {
    // 동일한 구현
}
```

---

## 설정 프로퍼티 변경

```properties
# 이전
spring.jackson.read.default-property-inclusion=non_null
spring.jackson.read.use-big-decimal-for-floats=true

# 이후
spring.jackson.json.read.default-property-inclusion=non_null
spring.jackson.json.read.use-big-decimal-for-floats=true
```

전체 프로퍼티 변경 목록은 `spring-boot-properties-migrator`를 실행하면 로그에 출력된다.

---

## Jackson 2 하위 호환 모듈

기존 Jackson 2 기반 코드를 당장 모두 변경하기 어려운 경우,
deprecated `spring-boot-jackson2` 모듈을 통해 Jackson 2를 계속 사용할 수 있다.

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-jackson2</artifactId>
</dependency>
```

> **임시 조치**다. Jackson 3으로의 전환 계획을 수립하라.
> `spring-boot-jackson2`는 향후 버전에서 제거될 예정이다.

---

## import 경로 마이그레이션 빠른 참조

```java
// 이전 import
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

// 이후 import
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.boot.autoconfigure.jackson.JsonMapperBuilderCustomizer;
```

---

## 체크리스트

- [ ] `com.fasterxml.jackson` import를 `tools.jackson`으로 변경
- [ ] `@JsonComponent` → `@JacksonComponent`
- [ ] `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`
- [ ] `spring.jackson.read.*` → `spring.jackson.json.read.*`
- [ ] Maven/Gradle 의존성 Group ID 변경 (BOM 사용 시 자동 처리)
- [ ] Jackson 2 직접 의존이 있다면 `spring-boot-jackson2` 임시 추가 또는 Jackson 3으로 전환
