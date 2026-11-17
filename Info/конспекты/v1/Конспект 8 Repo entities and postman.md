# Конспект 8: ProjectMember, TestSuite, TestCase, Feign testCaseId (готово)

## 1. Итоговое состояние

| Что | Статус |
|---|---|
| Инфраструктура (Docker) | ✅ Postgres 18 + Kafka 4.0 KRaft + Kafka UI 8090 |
| Flyway V1 | ✅ Обе схемы, `ddl-auto: validate` проходит |
| 4 сервиса в Eureka | ✅ 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**` |
| `tms-common` | ✅ **+ `ErrorCode` enum** — все коды через него |
| `Project`, `User` (Repo) | ✅ Полный CRUD |
| `TestRun`, `TestResult`, `BugReport` (Exec) | ✅ Полный CRUD + Feign-валидация + Kafka |
| **`ProjectMember` (Repo)** | ✅ **Embeddable PK, `@Query` для `existsBy*`** |
| **`TestSuite` (Repo)** | ✅ **Иерархия parent/child + cycle-detection** |
| **`TestCase` (Repo)** | ✅ **`steps` как JSONB (`@JdbcTypeCode(SqlTypes.JSON)`)** |
| **Feign `TestCaseClient` в Exec** | ✅ **Валидация `testCaseId` в `TestResultService.create`** |
| Все 7 сущностей из Конспекта 1 | ✅ **Доменная модель исчерпана** |

---

## 2. Стек (не менять)

| Слой | Версия |
|---|---|
| Java | 21 LTS |
| Spring Boot | 4.0.x (milestone) |
| Spring Cloud | 2025.1.x (milestone) |
| Kafka | 4.0 KRaft |
| PostgreSQL | 18 |
| Flyway | 11.x + **`flyway-database-postgresql` явно в pom** |
| Angular | 22.x (ещё не начат) |
| Node.js | 24 LTS |

**Maven multi-module `tms`**, все дочерние от `com.example.tms:tms`. **Базовый пакет — `org.learn`**.

**Порты:** 8761 Eureka / 8080 Gateway / 8081 Repo / 8082 Exec / 5432 / 9092 / 8090 Kafka UI.

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

**Актуальный словарь кодов (status):**

| code | status | источник |
|---|---|---|
| `USER_NOT_FOUND` | 404 | Repo: `UserService`, `ProjectMemberService`, `TestCaseService`; Exec: Feign |
| `USERNAME_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `EMAIL_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `PROJECT_NOT_FOUND` | 404 | Repo: `ProjectService`, `ProjectMemberService`, `TestSuiteService`; Exec: Feign |
| `PROJECT_KEY_ALREADY_EXISTS` | 409 | Repo: `ProjectService` |
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

**Правило:** новый слайс → сначала пополняем `ErrorCode`, потом код.

### 3.2. Три уровня защиты от `null` (DB `NOT NULL` + `DEFAULT`)

1. **Mapper**: `@Mapping(target="<field>", defaultValue="…")`
2. **Entity**: `@PrePersist void prePersist() { if (field == null) field = <default>; }`
3. **Service**: явная проверка после маппинга

### 3.3. MapStruct `ignore=true`

Всегда: `id`, `createdAt`, `updatedAt`.  
Управляемые сервисом: `passwordHash` (User), `status` (TestRun, TestCase), `executedAt` (TestResult), `resolvedAt` (BugReport).  
Иммутабельные в update-маппере: `projectId`/`createdById` (TestRun), `testRunId`/`testCaseId` (TestResult), `projectId`/`reporterId`/`testResultId` (BugReport), `suiteId`/`createdById` (TestCase), `projectId` (TestSuite).

### 3.4. Security

Только `spring-security-crypto` (BCrypt), **не** `-starter-security`.

### 3.5. Maven / Lombok / MapStruct

`maven-compiler-plugin` с `<annotationProcessorPaths>` **строго в порядке**:
1. `spring-boot-configuration-processor`
2. `lombok`
3. `lombok-mapstruct-binding` (**0.2.0**)
4. `mapstruct-processor`

Без `annotationProcessorPaths` — MapStruct/Lombok молча не работают.

### 3.6. Автоконфигурация `tms-common`

`AutoConfiguration.imports` **не подхватывается** — в главных классах сервисов явно:

```java
@SpringBootApplication
@Import(CommonAutoConfiguration.class)
public class ...Application { }
```

### 3.7. Flyway

`flyway-database-postgresql` — **явная зависимость в pom**. `ddl-auto: validate` всегда. Схему меняет только Flyway. V1 forward-only.

### 3.8. Время

Все timestamps в entity — `OffsetDateTime` (под `TIMESTAMPTZ`). **Не** `LocalDateTime`.

### 3.9. Cross-service

Только UUID, **без FK**. Валидация ссылок — Feign. FK — только внутри своей схемы. В entity скалярный `UUID`, **не** `@ManyToOne`.

### 3.10. Feign

- `@FeignClient(name="test-repository-service", contextId="<name>")` — `contextId` **обязателен** при нескольких клиентах к одному сервису.
- Пути **без** `/api/...` (Feign идёт через Eureka, не через Gateway).
- `checkExists` возвращает `void` — нужен только 200 OK.
- **Catch `FeignException.NotFound` ДО `FeignException`** — иначе 404 маскируется под 503.
- `@EnableFeignClients(basePackages = "org.learn.execution.client")`.

**Клиенты в Exec (`org.learn.execution.client`):**
- `ProjectClient` (`contextId="projectClient"`) — `GET /projects/{id}`
- `UserClient` (`contextId="userClient"`) — `GET /users/{id}`
- `TestCaseClient` (`contextId="testCaseClient"`) — `GET /test-cases/{id}`

### 3.11. Kafka

- Топик: `test-run-events` (`KafkaTopics.TEST_RUN_EVENTS`)
- Key: `testRunId` (String)
- Value: `TestRunFinishedEvent` (record в `tms-common`)
- `spring.json.trusted.packages: "org.learn.*"` в обоих сервисах
- Producer: `KafkaTemplate<Object,Object>` + `send(...).whenComplete(...)`
- Consumer: `@EnableKafka` + `@KafkaListener` в `TestRunFinishedListener` (Repo), `groupId="test-repository-service"`
- **Пока только логирует** — persistence в `audit_log` через Flyway V2 = следующий шаг

### 3.12. JPA — особенности

**Embedded ID (`ProjectMember`):**
- `@EmbeddedId private ProjectMemberId id;`
- `@Embeddable` класс с `@Column(name="project_id")` / `@Column(name="user_id")` + `implements Serializable` + `@EqualsAndHashCode`
- **Spring Data не выводит пути через embedded ID для `existsBy<Id>Property`** → нужны **явные `@Query`**:

```java
@Query("select count(pm) > 0 from ProjectMember pm " +
       "where pm.id.projectId = :projectId and pm.id.userId = :userId")
boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

@Query("select count(pm) > 0 from ProjectMember pm " +
       "where pm.id.projectId = :projectId")
boolean existsByProjectId(UUID projectId);
```

**JSONB (`TestCase.steps`):**
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "steps", columnDefinition = "jsonb")
private List<TestCaseStep> steps;
```
Где `TestCaseStep` — `record TestCaseStep(Integer order, String action, String expected)`.  
Импорты: `org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes`.

**Иерархия `TestSuite`** (self-FK `parent_suite_id`):
- Валидация parent: exists, belongs to same project, not self, no cycles (walk up the chain).
- Ошибки — `BadRequestException` с `TEST_SUITE_INVALID_PARENT`.

### 3.13. Gateway

WebFlux-only, **без** `spring-boot-starter-web`. `discovery.locator.enabled: false`, `StripPrefix=2`.

### 3.14. Три места согласования схемы

`hikari.schema` + `hibernate.default_schema` + `flyway.schemas`/`default-schema` — должны совпадать.

---

## 4. Enum-значения (строго по Конспекту 1, дублируются в CHECK БД)

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

---

## 5. `tms-common` — содержимое

- `enums/*` — 11 enum'ов + **`ErrorCode`**
- `dto/ErrorResponse`, `dto/PageResponse` (с фабрикой `PageResponse.of(Page<T>)`)
- `exception/ApiException` (база) + `ResourceNotFoundException`, `ConflictException`, `BadRequestException`, `ServiceUnavailableException` — сигнатура `(String code, String message)`
- `exception/GlobalExceptionHandler` — `ApiException`, `DataIntegrityViolationException`(→409), `MethodArgumentNotValidException`(→400), `Exception`(→500)
- `consts/KafkaTopics` — `TEST_RUN_EVENTS = "test-run-events"`
- `event/TestRunFinishedEvent` (record + вложенный `Summary`):
  ```java
  record TestRunFinishedEvent(String eventType, UUID testRunId, UUID projectId,
      String status, OffsetDateTime finishedAt, Summary summary, UUID triggeredBy) {
      record Summary(int total, int passed, int failed, int blocked) {}
  }
  ```
- `autoconfig/CommonAutoConfiguration` (подключается через `@Import`)
- Зависимости `jakarta.servlet-api` и `spring-tx` — scope **`provided`**

---

## 6. Repository Service — реализованные слайсы

### 6.1. `User` (`/users`)

POST / GET (paginated) / GET by id / GET by username / PUT / PATCH `/password` / DELETE (soft: `active=false`). BCrypt, `passwordHash` никогда не отдаётся.

### 6.2. `Project` (`/projects`)

POST / GET (paginated+filter) / GET by id / GET by key / PUT / DELETE. `ownerId` — скалярный `UUID`.

### 6.3. `ProjectMember` (`/project-members`)

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/project-members` | 201 + Location |
| GET | `/project-members?projectId=&userId=&page=&size=&sort=` | 200 |
| GET | `/project-members/{projectId}/{userId}` | 200 / 404 |
| PUT | `/project-members/{projectId}/{userId}` | 200 / 404 |
| DELETE | `/project-members/{projectId}/{userId}` | 204 / 404 |

**Файлы:**
- `domain/ProjectMemberId.java` (`@Embeddable`)
- `domain/ProjectMember.java` (`@EmbeddedId`)
- `dto/projectmember/{ProjectMemberCreateRequest, ProjectMemberUpdateRequest, ProjectMemberResponse}`
- `mapper/ProjectMemberMapper.java` — `@Mapping(target="id", expression="java(new ProjectMemberId(request.getProjectId(), request.getUserId()))")`
- `repository/ProjectMemberRepository.java` — **`@Query` для `existsBy*`** (см. §3.12)
- `service/ProjectMemberService.java` — валидирует `projectId`/`userId` через локальные репо, ловит дубликат `409 PROJECT_MEMBER_ALREADY_EXISTS`
- `controller/ProjectMemberController.java`

### 6.4. `TestSuite` (`/test-suites`)

POST / GET (paginated) / GET by id / PUT / DELETE.

**Файлы:**
- `domain/TestSuite.java` — self-FK `parentSuiteId` как скалярный `UUID`
- `dto/testsuite/{TestSuiteCreateRequest, TestSuiteUpdateRequest, TestSuiteResponse}`
- `mapper/TestSuiteMapper.java`
- `repository/TestSuiteRepository.java` — `existsByProjectIdAndName`, `JpaSpecificationExecutor`
- `service/TestSuiteService.java` — **`validateParentSuite`** (exists, same project, not self, no cycle)
- `controller/TestSuiteController.java`

### 6.5. `TestCase` (`/test-cases`)

POST / GET (paginated) / GET by id / PUT / DELETE.

**Файлы:**
- `domain/TestCaseStep.java` — record `(Integer order, String action, String expected)`
- `domain/TestCase.java` — `steps` как `@JdbcTypeCode(SqlTypes.JSON) List<TestCaseStep>`
- `dto/testcase/{TestCaseCreateRequest, TestCaseUpdateRequest, TestCaseResponse}`
- `mapper/TestCaseMapper.java`
- `repository/TestCaseRepository.java` — `existsBySuiteId`, `JpaSpecificationExecutor`
- `service/TestCaseService.java` — валидирует `suiteId` (локально), `createdById` (локально)
- `controller/TestCaseController.java`

### 6.6. Kafka consumer

`TestRunFinishedListener` (`org.learn.repository.kafka`) — `@KafkaListener(topics=..., groupId="test-repository-service")`, **пока только логирует**. `@EnableKafka` в `config/KafkaConsumerConfig`.

---

## 7. Execution Service — реализованные слайсы

### 7.1. `TestRun` (`/test-runs`)

POST / GET (paginated+filter) / GET by id / PUT / PATCH start/complete/abort / DELETE.  
Feign-валидация `projectId` и `createdById` при create. Kafka producer в `complete()`.

**Матрица переходов:**

| Текущий | start | complete | abort | update | delete |
|---|---|---|---|---|---|
| CREATED | → IN_PROGRESS | ❌ 409 | → ABORTED | ✅ | ✅ |
| IN_PROGRESS | ❌ 409 | → COMPLETED (+finishedAt, +Kafka) | → ABORTED | ❌ 409 | ❌ 409 |
| COMPLETED / ABORTED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |

### 7.2. `TestResult` (`/test-results`)

POST / GET (paginated+filter) / GET by id / PUT / DELETE.

**Валидация при create/update/delete:**
- `testRunId` — прогон должен быть **IN_PROGRESS** (`409 TEST_RUN_INVALID_STATE`)
- `testCaseId` — **Feign `TestCaseClient.checkExists`** (`404 TEST_CASE_NOT_FOUND`, `503 REPOSITORY_UNAVAILABLE`)
- `executedById` (опц.) — Feign `UserClient.checkExists` (`404 USER_NOT_FOUND`)
- UNIQUE(test_run_id, test_case_id) → `409 TEST_RESULT_ALREADY_EXISTS`
- `executedAt` ставит сервис

**`total` в summary включает SKIPPED.**

### 7.3. `BugReport` (`/bug-reports`)

POST / GET (paginated + filters projectId/status/severity/assigneeId/reporterId) / GET by id / PUT / DELETE. **Без Kafka**, **без soft-delete** (в схеме нет `active`).

**Валидация при create:**
- `projectId` — Feign `ProjectClient` (`404 PROJECT_NOT_FOUND`)
- `reporterId` — Feign `UserClient` (`404 USER_NOT_FOUND`)
- `assigneeId` (опц.) — Feign `UserClient`
- `testResultId` (опц.) — **локально** `testResultRepository.existsById` (`404 TEST_RESULT_NOT_FOUND`)

**Бизнес-правило:** при переводе в `RESOLVED`/`CLOSED` — проставить `resolvedAt = now()` если ещё null. `status` при create всегда `OPEN`, `resolvedAt = null` (ignore в маппере).

**Файлы:**
- `domain/BugReport.java`
- `dto/bugreport/{BugReportCreateRequest, BugReportUpdateRequest, BugReportResponse}`
- `mapper/BugReportMapper.java` — `ignore` для `id`, `status`(create), `resolvedAt`, `createdAt`, `updatedAt`; в update — `testResultId`, `projectId`, `reporterId`
- `repository/BugReportRepository.java` (`JpaSpecificationExecutor`)
- `service/BugReportService.java` — spec-фильтрация
- `controller/BugReportController.java`

### 7.4. Feign-клиенты

- `ProjectClient` (`contextId="projectClient"`)
- `UserClient` (`contextId="userClient"`)
- **`TestCaseClient` (`contextId="testCaseClient"`)** — добавлен на этом шаге

### 7.5. Kafka producer

`TestRunService.complete()` → `kafkaTemplate.send(topic, testRunId.toString(), event).whenComplete(...)`. Summary считается через `testResultRepository.countByTestRunId*`.

---

## 8. Проверки (опорные команды)

```bash
# Eureka
curl -s http://localhost:8761/eureka/apps | grep -E '<name>|<status>'   # 3 UP

# Gateway
curl -s http://localhost:8080/api/repository/actuator/health
curl -s http://localhost:8080/api/execution/actuator/health

# Flyway
docker exec -it tms-postgres psql -U tms -d tms -c \
  "SELECT 'repo',version,success FROM test_repository.flyway_schema_history
   UNION ALL SELECT 'exec',version,success FROM test_execution.flyway_schema_history;"

# Kafka consumer lag
docker exec -it tms-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group test-repository-service
# LAG = 0 после обработки

# JSONB steps
docker exec -it tms-postgres psql -U tms -d tms \
  -c "SELECT id, title, steps FROM test_repository.test_cases LIMIT 1;"
```

**E2E smoke:** create project → user → testSuite → testCase → testRun → start → testResult(PASSED) → testResult(FAILED) с `bugReport` → complete → в логах Exec `Published TestRunFinished: summary=...`, в логах Repo `Received TestRunFinished: ...`.

---

## 9. Roadmap — где мы

1. ✅ `tms-common` + `ErrorCode`
2. ✅ `Project`, `User` (Repo)
3. ✅ `TestRun`, `TestResult`, `BugReport` (Exec)
4. ✅ Kafka producer/consumer `TestRunFinished`
5. ✅ `ProjectMember`, `TestSuite`, `TestCase` (Repo)
6. ✅ Feign `testCaseId` в `TestResultService.create`
7. ⏭️ **Flyway V2 `audit_log`** в `test_repository` + persistence в `TestRunFinishedListener` (закрывает TODO consumer'а)
8. ⏭️ **Testcontainers** + `application-test.yml` + профиль `it` + интеграционные тесты
9. ⏭️ **Angular 22 frontend** (скелет + первые экраны: Projects, TestRuns, TestResults)
10. ⏭️ **Security**: JWT в Gateway + `@PreAuthorize` в сервисах
11. ⏭️ **Observability**: Micrometer + Prometheus + Grafana + Tracing

---

## 10. Как продолжить в новом чате (copy-paste)

> Продолжаем TMS. **Стек:** Java 21, Spring Boot 4.0.x (milestone), Spring Cloud 2025.1.x (milestone), Kafka 4.0 KRaft, Postgres 18, Angular 22 (ещё не начат). Maven multi-module `tms`, **пакет `org.learn`**, все дочерние от `com.example.tms:tms`. Порты: 8761 Eureka / 8080 Gateway / 8081 Repo / 8082 Exec / 5432 / 9092 / 8090 Kafka UI.
>
> **Инфраструктура рабочая:** Docker Compose — Postgres 18 (схемы `test_repository` + `test_execution`), Kafka 4.0 KRaft, Kafka UI 8090. Flyway V1 применён в обе схемы, `ddl-auto: validate` проходит. Все 4 сервиса стартуют, Eureka видит 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**`.
>
> **Готово (все 7 сущностей из Конспекта 1):**
> - `tms-common`: 11 enum'ов + **`ErrorCode`** (единый источник кодов), `ErrorResponse`, `PageResponse` (+`of(Page)`), `GlobalExceptionHandler`, `KafkaTopics.TEST_RUN_EVENTS`, `TestRunFinishedEvent` (record + `Summary`). Подключён через `@Import(CommonAutoConfiguration.class)`.
> - Repo: `Project`, `User` (BCrypt, soft-delete), **`ProjectMember`** (EmbeddedId, `@Query` для `existsBy*`), **`TestSuite`** (иерархия + cycle-detection), **`TestCase`** (JSONB `steps`). Kafka consumer `TestRunFinishedListener` — пока только логирует.
> - Exec: `TestRun` (lifecycle + Kafka producer + Feign projectId/createdById), `TestResult` (guard IN_PROGRESS + UNIQUE + **Feign `TestCaseClient`** для `testCaseId` + Feign userId), `BugReport` (filters через Specification, `resolvedAt` при RESOLVED/CLOSED, Feign projectId/reporterId/assigneeId + локально testResultId). **Feign-клиенты:** `ProjectClient`, `UserClient`, `TestCaseClient` — все `contextId` заданы.
>
> **Правила проекта (критично):**
> 1. **Все коды исключений — через `ErrorCode` enum**. Сигнатура `(String code, String message)` — передаём `ErrorCode.X.getCode()`. Новый слайс → сначала пополняем `ErrorCode`, потом код. Текущий словарь: `USER_NOT_FOUND`, `USERNAME_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS`, `PROJECT_NOT_FOUND`, `PROJECT_KEY_EXISTS`, `PROJECT_MEMBER_NOT_FOUND`, `PROJECT_MEMBER_ALREADY_EXISTS`, `TEST_SUITE_NOT_FOUND`, `TEST_SUITE_INVALID_PARENT`, `TEST_CASE_NOT_FOUND`, `TEST_RUN_NOT_FOUND`, `TEST_RUN_INVALID_STATE`, `TEST_RESULT_NOT_FOUND`, `TEST_RESULT_ALREADY_EXISTS`, `BUG_REPORT_NOT_FOUND`, `REPOSITORY_UNAVAILABLE`.
> 2. Поля с DB `NOT NULL` + `DEFAULT` — **3 уровня защиты**: `@Mapping(defaultValue=…)` + `@PrePersist` + явная проверка в service.
> 3. MapStruct `ignore=true`: `id`, `createdAt`, `updatedAt` (всегда); `passwordHash`, `status` (TestRun, TestCase), `executedAt` (TestResult), `resolvedAt` (BugReport); иммутабельные в update: `projectId`/`createdById` (TestRun), `testRunId`/`testCaseId` (TestResult), `projectId`/`reporterId`/`testResultId` (BugReport), `suiteId`/`createdById` (TestCase), `projectId` (TestSuite).
> 4. Только `spring-security-crypto`, **не** `-starter-security`.
> 5. `annotationProcessorPaths` **строго**: `spring-boot-configuration-processor` → `lombok` → `lombok-mapstruct-binding:0.2.0` → `mapstruct-processor`.
> 6. Cross-service — только UUID, без FK. Валидация через Feign. **`@FeignClient(contextId=…)` обязателен**. Пути Feign **без** `/api/...`. Catch `FeignException.NotFound` **ДО** `FeignException`.
> 7. Время — `OffsetDateTime`, не `LocalDateTime`.
> 8. `flyway-database-postgresql` — явно в pom. `ddl-auto: validate` всегда.
> 9. Kafka: топик `test-run-events`, key=`testRunId`, событие `TestRunFinished`. `trusted.packages: org.learn.*`. `@EnableKafka` для consumer.
> 10. Gateway — WebFlux-only, без `starter-web`. `discovery.locator.enabled: false`, `StripPrefix=2`.
> 11. Три места схемы: `hikari.schema`, `hibernate.default_schema`, `flyway.schemas`/`default-schema`.
> 12. **Embedded ID + Spring Data:** `existsBy<EmbeddedIdProperty>` **не выводится** — нужны явные `@Query` (`select count(pm) > 0 from ... where pm.id.projectId = :projectId`).
> 13. **JSONB-поля:** `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` + record-value object. Импорты `org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes`.
>
> **Следующий шаг:** **Flyway V2 `audit_log`** в `test_repository` (таблица + V2 файл) + persistence в `TestRunFinishedListener` (заменить `log.info` на `auditLogRepository.save(...)`). Затем Testcontainers + интеграционные тесты. Скажи, с чего начинаем, и выдай код.
>
> Готов формировать `Flyway V2 audit_log + persistence consumer'а`.