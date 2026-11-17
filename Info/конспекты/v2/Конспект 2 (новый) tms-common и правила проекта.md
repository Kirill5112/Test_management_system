# Конспект 2 (новый): `tms-common` и правила проекта

> Объединённая версия правил из Конспектов 4–8. Финальные версии словаря ошибок, правил MapStruct/Feign/Kafka/JPA. Убраны промежуточные состояния справочников. Всё, что нужно знать перед написанием любого нового слайса.

---

## 1. Модуль `tms-common` — содержимое

Назначение: общие сущности, не привязанные к конкретному сервису. Подключён к обоим бизнес-сервисам.

```
tms-common/src/main/java/org/learn/
├── enums/                          ← 11 enum'ов + ErrorCode
├── dto/ErrorResponse.java
├── dto/PageResponse.java           ← фабрика PageResponse.of(Page<T>)
├── exception/ApiException.java     ← база
├── exception/ResourceNotFoundException.java
├── exception/ConflictException.java
├── exception/BadRequestException.java
├── exception/ServiceUnavailableException.java
├── exception/GlobalExceptionHandler.java
├── consts/KafkaTopics.java         ← TEST_RUN_EVENTS = "test-run-events"
├── event/TestRunFinishedEvent.java ← record + вложенный Summary
└── autoconfig/CommonAutoConfiguration.java
```

### 1.1. Зависимости `tms-common`

`jakarta.servlet-api` и `spring-tx` (для `DataIntegrityViolationException`) добавлены со scope **`provided`**. В рантайме они придут из `starter-web` и `starter-data-jpa` сервисов-потребителей.

### 1.2. ⚠️ Проблема с автоконфигурацией

Файл `META-INF/spring/...AutoConfiguration.imports` **не подхватывается** (структура сборки Maven/IDEA).

**Решение:** в главных классах `@SpringBootApplication` бизнес-сервисов — явный импорт:

```java
@SpringBootApplication
@Import(CommonAutoConfiguration.class)
public class TestRepositoryServiceApplication { }
```

Это гарантированно регистрирует `GlobalExceptionHandler` в контексте. Без этого ошибки БД возвращали 500 вместо 409.

---

## 2. Enum-значения (строго по Конспекту 1, дублируются в `CHECK` БД)

| Enum | Значения |
|---|---|
| `UserRole` | ADMIN / MANAGER / TESTER / DEVELOPER |
| `ProjectStatus` | ACTIVE / ARCHIVED |
| `ProjectRole` | MANAGER / TESTER / DEVELOPER |
| `TestCasePriority` | LOW / MEDIUM / HIGH / CRITICAL |
| `TestCaseType` | MANUAL / AUTOMATED |
| `TestCaseStatus` | DRAFT / ACTIVE / DEPRECATED |
| `TestRunStatus` | CREATED / IN_PROGRESS / COMPLETED / ABORTED |
| `TestResultStatus` | PASSED / FAILED / BLOCKED / SKIPPED |
| `BugStatus` | OPEN / IN_PROGRESS / RESOLVED / CLOSED / REJECTED |
| `BugSeverity` | BLOCKER / CRITICAL / MAJOR / MINOR / TRIVIAL |
| `BugPriority` | HIGH / MEDIUM / LOW |

Продублированы в `CHECK`-констрейнтах БД (V1). При изменении enum — миграция БД обязательна.

---

## 3. ⚠️ ПРАВИЛА ПРОЕКТА (критично, не повторять ошибок)

### 3.1. `ErrorCode` enum — единый источник кодов

**Все коды исключений — через `org.learn.enums.ErrorCode`**, никаких «голых» строк.

```java
public enum ErrorCode {
    PROJECT_NOT_FOUND,
    PROJECT_KEY_EXISTS,
    BUG_REPORT_NOT_FOUND,
    REPOSITORY_UNAVAILABLE,
    USER_NOT_FOUND,
    TEST_RESULT_NOT_FOUND,
    USERNAME_ALREADY_EXISTS,
    EMAIL_ALREADY_EXISTS,
    TEST_RESULT_ALREADY_EXISTS,
    TEST_RUN_NOT_FOUND,
    TEST_RUN_INVALID_STATE,
    PROJECT_MEMBER_NOT_FOUND,
    PROJECT_MEMBER_ALREADY_EXISTS,
    TEST_SUITE_NOT_FOUND,
    TEST_SUITE_INVALID_PARENT,
    TEST_CASE_NOT_FOUND;
}
```

**Сигнатура исключений:** `(String code, String message)` — передаём `ErrorCode.X.getCode()`. Если решишь переделать на конструктор, принимающий `ErrorCode` — валидно, но тогда поменяй **везде** сразу.

### 3.2. Словарь кодов (актуальный, status)

| code | status | источник |
|---|---|---|
| `USER_NOT_FOUND` | 404 | Repo: `UserService`, `ProjectMemberService`, `TestCaseService`; Exec: Feign |
| `USERNAME_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `EMAIL_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `PROJECT_NOT_FOUND` | 404 | Repo: `ProjectService`, `ProjectMemberService`, `TestSuiteService`; Exec: Feign |
| `PROJECT_KEY_EXISTS` | 409 | Repo: `ProjectService` |
| `PROJECT_MEMBER_NOT_FOUND` | 404 | Repo: `ProjectMemberService` |
| `PROJECT_MEMBER_ALREADY_EXISTS` | 409 | Repo: `ProjectMemberService` |
| `TEST_SUITE_NOT_FOUND` | 404 | Repo: `TestSuiteService`, `TestCaseService` |
| `TEST_SUITE_INVALID_PARENT` | 400 | Repo: `TestSuiteService` (self-parent, чужой project, cycle) |
| `TEST_CASE_NOT_FOUND` | 404 | Repo: `TestCaseService`; Exec: `TestResultService` (Feign) |
| `TEST_RUN_NOT_FOUND` | 404 | Exec: `TestRunService`, `TestResultService` |
| `TEST_RUN_INVALID_STATE` | 409 | Exec: переходы статусов |
| `TEST_RESULT_NOT_FOUND` | 404 | Exec: `TestResultService`, `BugReportService` |
| `TEST_RESULT_ALREADY_EXISTS` | 409 | Exec: UNIQUE(test_run_id, test_case_id) |
| `BUG_REPORT_NOT_FOUND` | 404 | Exec: `BugReportService` |
| `REPOSITORY_UNAVAILABLE` | 503 | Exec: все Feign-клиенты при не-404 |

**Правило:** новый слайс → **сначала пополняем `ErrorCode`**, потом код. Никаких «голых» строковых кодов.

### 3.3. Сигнатуры исключений

```java
new ResourceNotFoundException(code, message)   // → 404
new ConflictException(code, message)           // → 409
new BadRequestException(code, message)         // → 400
new ServiceUnavailableException(code, message) // → 503
```

`code` — `SCREAMING_SNAKE_CASE` с префиксом сущности. `message` — для человека.

### 3.4. `GlobalExceptionHandler` — что перехватывает

- `ApiException` (база всех кастомных) → соответствующий HTTP-status по типу
- `DataIntegrityViolationException` → **409** (constraint violation)
- `MethodArgumentNotValidException` → **400**
- `Exception` (fallback) → **500**

---

## 4. Три уровня защиты от `null` (для полей с DB `NOT NULL` + `DEFAULT`)

**Симптом-триггер:** `ERROR: null value in column "active" of relation "users" violates not-null constraint`.

**Причина:** `@Builder.Default` **не** спасает, когда MapStruct/Jackson проставили `null` явно. Поле «трогали» — default не применился.

**Паттерн (применять ко всем полям с DB-`NOT NULL` + DB `DEFAULT`):**

1. **Mapper** — `@Mapping(target = "<field>", source = "<field>", defaultValue = "true")` (или `"ACTIVE"` для enum'ов, `"0"` для чисел).
2. **Entity** — `@PrePersist` хук:
   ```java
   @PrePersist
   void prePersist() {
       if (active == null) active = Boolean.TRUE;
   }
   ```
3. **Service** — явная страховка после маппинга:
   ```java
   if (user.getActive() == null) user.setActive(Boolean.TRUE);
   ```

Одного уровня хватило бы, но держим все три — дёшево и защищает от регрессий при регенерации маппера или смене версии Lombok/MapStruct.

**Исключение:** поля с `@NotNull` в DTO (например, `UserUpdateRequest.active`) — валидация отсечёт `null` до маппера, им хватает только `@NotNull`.

---

## 5. MapStruct — правила мапперов

### 5.1. Обязательные `ignore=true`

**Всегда** (для create/update):
- `id`, `createdAt`, `updatedAt`

**Управляемые сервисом** (никогда не маппятся напрямую из request):
- `passwordHash` (User) — ставится в сервисе через `passwordEncoder.encode(...)`
- `status` (TestRun) — ставится в сервисе
- `status` (TestCase) — ставится в сервисе
- `executedAt` (TestResult) — ставится в сервисе (`OffsetDateTime.now()`)
- `resolvedAt` (BugReport) — ставится в сервисе при переходе в RESOLVED/CLOSED
- `status` (BugReport) — при create всегда `OPEN`, ставится в сервисе

**Иммутабельные в update-маппере** (нельзя менять после create):
- `projectId` / `createdById` (TestRun)
- `testRunId` / `testCaseId` (TestResult)
- `projectId` / `reporterId` / `testResultId` (BugReport)
- `suiteId` / `createdById` (TestCase)
- `projectId` (TestSuite)

### 5.2. Порядок `annotationProcessorPaths` — строго

Без правильного порядка MapStruct/Lombok **молча не работают**.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>spring-boot-configuration-processor</path>
            <path>lombok</path>
            <path>lombok-mapstruct-binding (version 0.2.0)</path>
            <path>mapstruct-processor</path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Строго в порядке:**
1. `spring-boot-configuration-processor`
2. `lombok`
3. `lombok-mapstruct-binding:0.2.0`
4. `mapstruct-processor`

Блок `<executions>` **убран** — конфигурация применяется ко всем фазам по умолчанию.

**Симптом-триггер:** `cannot find symbol` (нет геттеров/сеттеров) + `variable ... not initialized in default constructor`. Причина — `annotationProcessorPaths` с неполным набором процессоров, Maven игнорирует процессоры из `classpath`.

---

## 6. Security

**Только `spring-security-crypto`** — даёт `BCryptPasswordEncoder` + `PasswordEncoder` интерфейс.

**НЕ `-starter-security`** — иначе включится авто-конфиг Security и все эндпоинты закроются за HTTP Basic.

Паттерн:
```java
@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

Поле `passwordHash` — `VARCHAR(255)`, BCrypt-хэш 60 символов укладывается с запасом. В `UserResponse` пароля **нет**.

---

## 7. Feign — правила

### 7.1. `@FeignClient` — обязательный `contextId`

```java
@FeignClient(name = "test-repository-service", contextId = "projectClient")
public interface ProjectClient { ... }

@FeignClient(name = "test-repository-service", contextId = "userClient")
public interface UserClient { ... }

@FeignClient(name = "test-repository-service", contextId = "testCaseClient")
public interface TestCaseClient { ... }
```

**`contextId` обязателен** при нескольких клиентах к одному сервису — иначе `BeanDefinitionOverrideException`.

### 7.2. Пути — без `/api/...`

Feign идёт через Eureka напрямую к сервису, **не через Gateway**. `StripPrefix=2` — только в Gateway.

```java
@GetMapping("/projects/{id}")   // правильно
// @GetMapping("/api/repository/projects/{id}")   ← НЕПРАВИЛЬНО
```

### 7.3. `checkExists` возвращает `void`

Нужен только факт `200 OK`. `404` → `FeignException.NotFound`.

```java
@GetMapping("/projects/{id}")
void checkExists(@PathVariable UUID id);
```

### 7.4. ⚠️ Порядок catch — критично

```java
try {
    projectClient.checkExists(projectId);
} catch (FeignException.NotFound e) {               // ← ПОДКЛАСС, ловим ПЕРВЫМ
    throw new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "...");
} catch (FeignException e) {                        // ← РОДИТЕЛЬ, ловим ВТОРЫМ
    throw new ServiceUnavailableException(ErrorCode.REPOSITORY_UNAVAILABLE, "...");
}
```

Иначе `404` маскируется под `503`.

### 7.5. `@EnableFeignClients`

```java
@SpringBootApplication
@EnableFeignClients(basePackages = "org.learn.execution.client")
@Import(CommonAutoConfiguration.class)
public class TestExecutionServiceApplication { }
```

### 7.6. `spring-cloud-starter-loadbalancer`

Должен быть на classpath (обычно транзитивно с `openfeign`). Проверять `mvn dependency:tree` при «No servers available for service».

---

## 8. Kafka — правила

### 8.1. Контракт

- **Topic:** `test-run-events` (константа `KafkaTopics.TEST_RUN_EVENTS`)
- **Key:** `testRunId` (String)
- **Value:** `TestRunFinishedEvent` (record в `tms-common`)
- **eventType:** `"TestRunFinished"`
- **`status` в событии — String** (не enum), контракт стабильнее

```java
public record TestRunFinishedEvent(
    String eventType,               // "TestRunFinished"
    UUID testRunId,
    UUID projectId,
    String status,                  // TestRunStatus.name()
    OffsetDateTime finishedAt,
    Summary summary,
    UUID triggeredBy
) {
    public record Summary(int total, int passed, int failed, int blocked) {}
}
```

`total` в `summary` **включает** `SKIPPED`.

### 8.2. Сериализация — авто-конфиг Boot

**Не создавать кастомный `KafkaProducerConfig` / `KafkaConsumerConfig`** — хватает авто-конфига.

`application.yml`:
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: test-repository-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "org.learn.*"   # ← ОБЯЗАТЕЛЬНО org.learn
```

**⚠ `spring.json.trusted.packages: "org.learn.*"`** — было `com.example.tms.*` (не совпадало с реальным пакетом, consumer падал на десериализации). Исправлено в обоих сервисах.

### 8.3. Producer

```java
kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, testRunId.toString(), event)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Failed to publish TestRunFinished: {}", testRunId, ex);
        } else {
            log.info("Published TestRunFinished: {} summary={}", testRunId, event.summary());
        }
    });
```

**Не блокирует HTTP-поток.** `KafkaTemplate<Object, Object>` — из авто-конфига.

### 8.4. Consumer

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig { }
```

```java
@Component
public class TestRunFinishedListener {
    @KafkaListener(topics = KafkaTopics.TEST_RUN_EVENTS, groupId = "test-repository-service")
    public void onTestRunFinished(TestRunFinishedEvent event) {
        log.info("Received TestRunFinished: {}", event);
    }
}
```

`groupId` совпадает с `spring.kafka.consumer.group-id`.

### 8.5. Проверка lag

```bash
docker exec -it tms-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group test-repository-service
# LAG = 0 после обработки
```

---

## 9. JPA — особенности

### 9.1. `EmbeddedId` (`ProjectMember`)

**Entity:**
```java
@Embeddable
@EqualsAndHashCode
public class ProjectMemberId implements Serializable {
    @Column(name = "project_id") private UUID projectId;
    @Column(name = "user_id")    private UUID userId;
}

@Entity
@Table(name = "project_members")
public class ProjectMember {
    @EmbeddedId
    private ProjectMemberId id;
    // ...
}
```

**⚠ Spring Data не выводит пути через embedded ID для `existsBy<Id>Property`** → нужны **явные `@Query`**:

```java
@Query("select count(pm) > 0 from ProjectMember pm " +
       "where pm.id.projectId = :projectId and pm.id.userId = :userId")
boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

@Query("select count(pm) > 0 from ProjectMember pm " +
       "where pm.id.projectId = :projectId")
boolean existsByProjectId(UUID projectId);
```

### 9.2. `JSONB`-поля (`TestCase.steps`)

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "steps", columnDefinition = "jsonb")
private List<TestCaseStep> steps;
```

Где `TestCaseStep` — `record TestCaseStep(Integer order, String action, String expected)`.

**Импорты:**
- `org.hibernate.annotations.JdbcTypeCode`
- `org.hibernate.type.SqlTypes`

### 9.3. Иерархия `TestSuite` (self-FK `parent_suite_id`)

В entity — скалярный `UUID parentSuiteId`, **не** `@ManyToOne`.

**Валидация `parentSuiteId` при create/update:**
1. `parentSuiteId` существует (если не null).
2. Принадлежит тому же `projectId`, что и suite.
3. `parentSuiteId != self.id`.
4. Нет cycle (walk up the chain — не встретить `self`).

Ошибки — `BadRequestException(ErrorCode.TEST_SUITE_INVALID_PARENT, ...)`.

### 9.4. Скалярный `UUID` вместо `@ManyToOne`

Паттерн проекта — **везде** скалярный `UUID` для cross-slice/cross-service связей. Даже внутри одной схемы (`TestResult.testRunId` → `TestRun` FK есть в БД, но в entity — `UUID testRunId`).

**Не вводим `@ManyToOne` / `@OneToMany` / `@JoinColumn`** — держим entity плоскими.

### 9.5. `JpaSpecificationExecutor` для фильтрации

Для сущностей с фильтрами (Project, TestRun, TestResult, BugReport, TestSuite, TestCase):

```java
public interface BugReportRepository extends JpaRepository<BugReport, UUID>,
                                              JpaSpecificationExecutor<BugReport> { }
```

Сервис собирает `Specification<BugReport>` из не-null параметров и делает `repository.findAll(spec, pageable)`.

---

## 10. Gateway — WebFlux-only

- **Без** `spring-boot-starter-web` — иначе ломаются gateway-фильтры, стартует servlet-стек.
- `discovery.locator.enabled: false` — маршруты только явные.
- `StripPrefix=2` — отрезает `/api/repository` / `/api/execution`.
- Проверка `mvn -q dependency:tree -pl api-gateway | grep starter-web` — пусто.

---

## 11. Схемы в трёх местах

Для корректной работы Postgres + Hibernate + Flyway **все три параметра** должны совпадать:

```yaml
spring.datasource.hikari.schema
spring.jpa.hibernate.default_schema
spring.flyway.default-schema      # + spring.flyway.schemas
```

Если разъедутся — Hibernate ищет таблицы не в той схеме, `ddl-auto: validate` падает с «missing table», хотя `\dt` показывает их на месте.

---

## 12. Время — `OffsetDateTime`

Все timestamps в entity — **`OffsetDateTime`** (под `TIMESTAMPTZ` в Postgres).

**Не** `LocalDateTime` — теряет timezone, `TIMESTAMPTZ` не конвертируется корректно.

`Instant` — ок как альтернатива, но в проекте выбран `OffsetDateTime`.

Автоматика:
- `@CreationTimestamp` на `createdAt`
- `@UpdateTimestamp` на `updatedAt`
- DB-дефолты `DEFAULT NOW()` — на случай прямых SQL-инсертов

---

## 13. Cross-service — только UUID, без FK

- Между сервисами **нет FK**. Валидация ссылок — на уровне приложения (Feign).
- FK — **только внутри своей схемы** (Repo: `projects.owner_id → users`, Exec: `test_results.test_run_id → test_runs`).
- В entity cross-service refs — скалярный `UUID`, не `@ManyToOne`.
- При create `TestRun` — Feign `ProjectClient.checkExists(projectId)` + `UserClient.checkExists(createdById)`.
- При create `TestResult` — Feign `TestCaseClient.checkExists(testCaseId)` + `UserClient.checkExists(executedById)` (если задан).
- При create `BugReport` — Feign `ProjectClient` + `UserClient` (reporter + assignee если задан) + **локально** `testResultRepository.existsById(testResultId)` (если задан, т.к. TestResult в той же схеме).

---

## 14. Lifecycle-переходы (справочник)

### 14.1. `TestRun`

| Текущий | start | complete | abort | update | delete |
|---|---|---|---|---|---|
| CREATED | → IN_PROGRESS | ❌ 409 | → ABORTED | ✅ | ✅ |
| IN_PROGRESS | ❌ 409 | → COMPLETED (+`finishedAt`, +Kafka) | → ABORTED | ❌ 409 | ❌ 409 |
| COMPLETED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |
| ABORTED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |

Ошибка перехода — `409 TEST_RUN_INVALID_STATE`.

### 14.2. `TestResult`

`create` / `update` / `delete` разрешены **только когда прогон `IN_PROGRESS`** (`409 TEST_RUN_INVALID_STATE` иначе). `list` / `getById` — всегда.

### 14.3. `BugReport`

- При create — `status = OPEN`, `resolvedAt = null` (в маппере `ignore`).
- При переходе в `RESOLVED` / `CLOSED` — проставить `resolvedAt = OffsetDateTime.now()` если ещё `null`.
- При возврате из `RESOLVED`/`CLOSED` в `OPEN`/`IN_PROGRESS` — `resolvedAt` **не сбрасывается** (история).
- Soft-delete **не применяется** (в схеме нет `active`) — обычный `DELETE`.

### 14.4. `User`

Soft-delete: `DELETE /users/{id}` → `active = false`. Hard-delete **невозможен** — `users.id` держат FK из `projects.owner_id`, `test_cases.created_by_id`, `project_members.user_id`.

---

## 15. Сводка правил (шпаргалка)

1. **`ErrorCode` enum** — единый источник, `SCREAMING_SNAKE_CASE` с префиксом сущности. Новый слайс → сначала `ErrorCode`, потом код.
2. Исключения — `(String code, String message)`.
3. Поля с DB `NOT NULL` + `DEFAULT` — **3 уровня защиты**: `@Mapping(defaultValue=…)` + `@PrePersist` + явная проверка в service.
4. MapStruct `ignore=true`: `id`, `createdAt`, `updatedAt` (всегда); `passwordHash`, `status` (TestRun/TestCase/BugReport-create), `executedAt`, `resolvedAt`; иммутабельные в update (`projectId`/`createdById`, `testRunId`/`testCaseId`, `suiteId`/`createdById`).
5. `annotationProcessorPaths` **строго**: `spring-boot-configuration-processor` → `lombok` → `lombok-mapstruct-binding:0.2.0` → `mapstruct-processor`. Без `<executions>`.
6. Только `spring-security-crypto`, **не** `-starter-security`.
7. Cross-service — только UUID, без FK. Валидация — Feign.
8. `@FeignClient(contextId=…)` **обязателен**. Пути без `/api/...`. Catch `FeignException.NotFound` **ДО** `FeignException`.
9. Время — `OffsetDateTime`, не `LocalDateTime`.
10. `flyway-database-postgresql` — явно в pom. `ddl-auto: validate` всегда.
11. Kafka: топик `test-run-events`, key=`testRunId`, `trusted.packages: org.learn.*`, `@EnableKafka` для consumer, producer через `send().whenComplete()`.
12. Gateway — WebFlux-only, без `starter-web`. `discovery.locator.enabled: false`, `StripPrefix=2`.
13. Три места схемы: `hikari.schema`, `hibernate.default_schema`, `flyway.schemas`/`default-schema`.
14. **Embedded ID:** `existsBy<EmbeddedIdProperty>` **не выводится** — нужны явные `@Query`.
15. **JSONB-поля:** `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` + record-value object.
16. `@Import(CommonAutoConfiguration.class)` в главных классах — иначе `GlobalExceptionHandler` не регистрируется.
17. Entity — плоские, **без `@ManyToOne`/`@OneToMany`**. Связи — скалярный `UUID`.

---