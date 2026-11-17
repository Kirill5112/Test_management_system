# Конспект 7: Kafka producer/consumer + слайс `TestResult` (готово)

## 1. Итоговое состояние

| Что | Статус |
|---|---|
| Инфраструктура (Docker) | ✅ Postgres 18 + Kafka 4.0 KRaft + Kafka UI 8090 |
| Flyway V1 | ✅ Обе схемы применены, `ddl-auto: validate` проходит |
| 4 сервиса в Eureka | ✅ 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**` |
| `tms-common` | ✅ DTO, 11 enum'ов, исключения (4 шт.), `GlobalExceptionHandler`, `KafkaTopics`, `TestRunFinishedEvent` |
| `Project` (Repo) | ✅ Полный CRUD |
| `User` (Repo) | ✅ Полный CRUD + BCrypt + soft-delete |
| `TestRun` (Exec) | ✅ Lifecycle-переходы + Feign-валидация `projectId`/`createdById` |
| **Kafka producer `TestRunFinished`** | ✅ Публикуется в `TestRunService.complete()`, key=`testRunId` |
| **Kafka consumer `TestRunFinished`** | ✅ В Repo: `TestRunFinishedListener` + `@EnableKafka` |
| **Слайс `TestResult` (Exec)** | ✅ Entity, Repo, Mapper, Service, Controller, DTO, lifecycle-guard по статусу прогона |
| **Реальный `summary` в событии** | ✅ `total/passed/failed/blocked` по `test_results` — заполняется при complete |
| `spring.json.trusted.packages` | ✅ Исправлен на `org.learn.*` в обоих сервисах |
| Smoke-тесты | ✅ Проходят (producer → consumer, summary=3/2/1/0, guard-переходы) |

**Базовая точка:** проект работает end-to-end по первому вертикальному потоку Project → User → TestRun → TestResult → Kafka event → consumer log.

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
| Angular | 22.x |
| Node.js | 24 LTS |

**Maven multi-module `tms`**, все дочерние от `com.example.tms:tms`. **Базовый пакет — `org.learn`** (это отличается от artifactId-родителя — не путать!).

**Порты:** 8761 Eureka / 8080 Gateway / 8081 Repo / 8082 Exec / 5432 / 9092 / 8090 Kafka UI.

---

## 3. Структура модулей

```
tms/
├── pom.xml                          (parent, packaging=pom)
├── tms-common/                      (DTO, enum'ы, исключения, Kafka-события)
├── eureka-server/                   (8761)
├── api-gateway/                     (8080, WebFlux-only, без starter-web)
├── test-repository-service/         (8081, схема test_repository)
└── test-execution-service/          (8082, схема test_execution)
```

**Gateway:** явные маршруты, `discovery.locator.enabled: false`, `StripPrefix=2`. `/api/repository/**` → `lb://test-repository-service`, `/api/execution/**` → `lb://test-execution-service`.

**Три места согласования схемы** (должны совпадать): `hikari.schema` + `hibernate.default_schema` + `flyway.schemas`/`default-schema`.

---

## 4. ⚠️ ПРАВИЛА ПРОЕКТА (критично, не повторять ошибок)

### 4.1. Исключения — сигнатура `(String code, String message)`

```java
new ResourceNotFoundException(code, message)   // → 404
new ConflictException(code, message)           // → 409
new BadRequestException(code, message)         // → 400
new ServiceUnavailableException(code, message) // → 503
```

**Словарь кодов (актуальный):**

| code | status | источник |
|---|---|---|
| `USER_NOT_FOUND` | 404 | Repo: `UserService`; Exec: `TestRunService`/`TestResultService` (Feign) |
| `USERNAME_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `EMAIL_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `PROJECT_NOT_FOUND` | 404 | Repo: `ProjectService`; Exec: `TestRunService` (Feign) |
| `PROJECT_KEY_ALREADY_EXISTS` | 409 | Repo: `ProjectService` |
| `TEST_RUN_NOT_FOUND` | 404 | Exec: `TestRunService`, `TestResultService` |
| `TEST_RUN_INVALID_STATE` | 409 | Exec: `TestRunService` (переход), `TestResultService` (результаты только в IN_PROGRESS) |
| `TEST_RESULT_NOT_FOUND` | 404 | Exec: `TestResultService` |
| `TEST_RESULT_ALREADY_EXISTS` | 409 | Exec: `TestResultService` (UNIQUE test_run_id+test_case_id) |
| `REPOSITORY_UNAVAILABLE` | 503 | Exec: Feign-клиенты при не-404 ошибке |

**Правило:** новый слайс → **сначала пополняем словарь**, потом код.

### 4.2. Три уровня защиты от `null` (для полей с DB `NOT NULL` + `DEFAULT`)

1. **Mapper**: `@Mapping(target="<field>", source="<field>", defaultValue="…")`
2. **Entity**: `@PrePersist void prePersist() { if (field == null) field = <default>; }`
3. **Service**: явная проверка после маппинга

Исключение: поля с `@NotNull` в DTO — валидация отсечёт null, им хватает только `@NotNull`.

### 4.3. MapStruct — обязательные `ignore=true`

- Всегда: `id`, `createdAt`, `updatedAt`
- `passwordHash` (User) — ставится в сервисе
- `status` (TestRun) — ставится в сервисе
- Иммутабельные поля в update-маппере: `projectId`/`createdById` (TestRun), `testRunId`/`testCaseId` (TestResult)
- `executedAt` (TestResult) — ставится в сервисе (`OffsetDateTime.now()`)

### 4.4. Security

Только `spring-security-crypto` (BCrypt), **не** `-starter-security`.

### 4.5. Maven / Lombok / MapStruct

`maven-compiler-plugin` с `<annotationProcessorPaths>` **строго в порядке**:
1. `spring-boot-configuration-processor`
2. `lombok`
3. `lombok-mapstruct-binding` (**0.2.0**)
4. `mapstruct-processor`

Без `annotationProcessorPaths` — MapStruct/Lombok молча не работают. `<executions>` блок убран.

### 4.6. Автоконфигурация `tms-common`

`AutoConfiguration.imports` **не подхватывается** — в главных классах бизнес-сервисов явно:

```java
@SpringBootApplication
@Import(CommonAutoConfiguration.class)
public class ...Application { }
```

### 4.7. Flyway

`flyway-database-postgresql` — **явная зависимость** в pom обоих сервисов. `ddl-auto: validate` всегда, схему меняет только Flyway. V1 forward-only, без `DROP IF EXISTS`.

### 4.8. Время

Все timestamps в entity — `OffsetDateTime` (под `TIMESTAMPTZ`). **Не** `LocalDateTime`.

### 4.9. Cross-service

Только UUID, **без FK**. Валидация ссылок — Feign. FK — только внутри своей схемы.
Внутри **одной** схемы между `test_runs` и `test_results` — FK есть (V1: `test_results.test_run_id → test_runs ON DELETE CASCADE`), но в **entity** используем скалярный `UUID` без `@ManyToOne` (паттерн проекта).

### 4.10. Feign

- `@FeignClient(name=…, contextId=…)` — `contextId` **обязателен** при нескольких клиентах к одному сервису.
- Пути в Feign — **без** `/api/...` (Feign идёт через Eureka, не через Gateway).
- `checkExists` возвращает `void` — нужен только `200 OK`.
- **Catch `FeignException.NotFound` ДО `FeignException`** — иначе 404 маскируется под 503.
- `@EnableFeignClients(basePackages = "org.learn.execution.client")`.

### 4.11. Kafka

- **Topic:** `test-run-events` (константа `KafkaTopics.TEST_RUN_EVENTS`).
- **Key:** `testRunId` (String).
- **Value:** `TestRunFinishedEvent` (record в `tms-common`).
- **Сериализация:** авто-конфиг Boot: `StringSerializer`+`JsonSerializer` / `StringDeserializer`+`JsonDeserializer`.
- **`spring.json.trusted.packages: "org.learn.*"`** (было `com.example.tms.*` — исправлено).
- **Producer:** использует `KafkaTemplate<Object,Object>` (автоконфиг), не блокирует HTTP: `send(...).whenComplete(...)`.
- **Consumer:** `@EnableKafka` + `@KafkaListener(topics=…, groupId="test-repository-service")`. `groupId` совпадает с `spring.kafka.consumer.group-id`.
- **`status` в событии — String** (не enum), контракт стабильнее.

### 4.12. Lifecycle-переходы `TestRun`

| Текущий | start | complete | abort | update | delete |
|---|---|---|---|---|---|
| CREATED | → IN_PROGRESS | ❌ 409 | → ABORTED | ✅ | ✅ |
| IN_PROGRESS | ❌ 409 | → COMPLETED (+`finishedAt`) | → ABORTED | ❌ 409 | ❌ 409 |
| COMPLETED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |
| ABORTED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |

`TestResult`: create/update/delete разрешены **только когда прогон `IN_PROGRESS`** (`409 TEST_RUN_INVALID_STATE` иначе). `list`/`getById` — всегда.

---

## 5. Enum-значения (строго по Конспекту 1, дублированы в CHECK БД)

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

## 6. `tms-common` — что внутри

- `enums/*` — все 11 enum'ов (см. §5).
- `dto/ErrorResponse`, `dto/PageResponse`.
- `exception/ApiException` (база) + `ResourceNotFoundException`, `ConflictException`, `BadRequestException`, **`ServiceUnavailableException`**.
- `exception/GlobalExceptionHandler` — перехватывает `ApiException`, `DataIntegrityViolationException` (→409), `MethodArgumentNotValidException` (→400), `Exception` (→500).
- `consts/KafkaTopics` — `TEST_RUN_EVENTS = "test-run-events"`.
- `event/TestRunFinishedEvent` (record):

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

- `autoconfig/CommonAutoConfiguration`.
- Зависимости `jakarta.servlet-api` и `spring-tx` — scope **`provided`**.

---

## 7. Repository Service — реализованные слайсы

### 7.1. `User` — эндпоинты

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/users` | 201 + Location |
| GET | `/users?page=&size=&sort=` | 200 |
| GET | `/users/{id}` | 200 / 404 |
| GET | `/users/by-username/{username}` | 200 / 404 |
| PUT | `/users/{id}` | 200 / 404 / 409 |
| PATCH | `/users/{id}/password` | 204 / 404 |
| DELETE | `/users/{id}` | 204 (soft-delete: `active=false`) |

`passwordHash` — BCrypt, `VARCHAR(255)`. Soft-delete обязателен: `users.id` держат FK из `projects.owner_id`, `test_cases.created_by_id`, `project_members.user_id`.

### 7.2. `Project` — эндпоинты

POST / GET (paginated+filter) / GET by id / GET by key / PUT / DELETE. `ownerId` — скалярный `UUID`, без `@ManyToOne`.

### 7.3. `TestRunFinishedListener` (Kafka consumer)

```
package org.learn.repository.kafka;
@KafkaListener(topics=KafkaTopics.TEST_RUN_EVENTS, groupId="test-repository-service")
```

Сейчас **только логирует**. Persistence в `audit_log` — следующий шаг (потребует Flyway V2).

`@EnableKafka` — в `config/KafkaConsumerConfig`.

---

## 8. Execution Service — реализованные слайсы

### 8.1. `TestRun` — эндпоинты

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/test-runs` | 201 / 404 / 503 (Feign-валидация) |
| GET | `/test-runs?projectId=&status=&page=&size=&sort=` | 200 |
| GET | `/test-runs/{id}` | 200 / 404 |
| PUT | `/test-runs/{id}` | 200 / 404 / 409 |
| PATCH | `/test-runs/{id}/start` | 200 / 404 / 409 |
| PATCH | `/test-runs/{id}/complete` | 200 / 404 / 409 (+ finishedAt + **publish Kafka**) |
| PATCH | `/test-runs/{id}/abort` | 200 / 404 / 409 |
| DELETE | `/test-runs/{id}` | 204 / 404 / 409 |

### 8.2. `TestResult` — эндпоинты

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/test-results` | 201 / 404 / 409 / 503 |
| GET | `/test-results?testRunId=&status=&page=&size=&sort=` | 200 / 404 |
| GET | `/test-results/{id}` | 200 / 404 |
| PUT | `/test-results/{id}` | 200 / 404 / 409 |
| DELETE | `/test-results/{id}` | 204 / 404 / 409 |

**Валидация:**
- `testRunId` — обязателен, прогон должен быть `IN_PROGRESS`.
- `testCaseId` — UNIQUE в рамках прогона (`existsByTestRunIdAndTestCaseId` → `409 TEST_RESULT_ALREADY_EXISTS`). **Feign-валидация на TestCase ещё не сделана** — TODO после слайса `TestCase` в Repo.
- `executedById` — опционален, но если задан → Feign `UserClient.checkExists` (`404 USER_NOT_FOUND`, `503 REPOSITORY_UNAVAILABLE`).
- `executedAt` — ставится сервисом при create и update (`OffsetDateTime.now()`).

**Файлы:** `domain/TestResult.java`, `repository/TestResultRepository.java` (с `countByTestRunId*`), `mapper/TestResultMapper.java`, `service/TestResultService.java`, `controller/TestResultController.java`, `dto/testresult/{Create,Update,Response}`.

### 8.3. Feign-клиенты (`org.learn.execution.client`)

- `ProjectClient` (`contextId="projectClient"`) — `checkExists(UUID)`.
- `UserClient` (`contextId="userClient"`) — `checkExists(UUID)`.
- Порядок catch в сервисах: `FeignException.NotFound` → `FeignException`.

### 8.4. Kafka producer

В `TestRunService.complete()`:
1. Валидирует статус (IN_PROGRESS).
2. Ставит `COMPLETED` + `finishedAt`.
3. Считает `summary` через `testResultRepository.countByTestRunId*` (4 запроса: total, passed, failed, blocked). `total` **включает SKIPPED**.
4. `kafkaTemplate.send(topic, testRunId.toString(), event).whenComplete(...)` — не блокирует, ошибки логирует.

**Не создавать кастомный `KafkaProducerConfig`** — хватает авто-конфига Boot.

---

## 9. Проверки (опорные команды)

```bash
# Eureka
curl -s http://localhost:8761/eureka/apps | grep -E '<name>|<status>'   # 3 UP

# Gateway + маршруты
curl -s http://localhost:8080/api/repository/actuator/health
curl -s http://localhost:8080/api/execution/actuator/health

# Flyway history
docker exec -it tms-postgres psql -U tms -d tms -c \
  "SELECT 'repo',version,success FROM test_repository.flyway_schema_history
   UNION ALL SELECT 'exec',version,success FROM test_execution.flyway_schema_history;"

# Kafka
docker exec -it tms-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group test-repository-service
# LAG должен быть 0 после complete + обработки consumer'ом
```

E2E smoke: create project → user → testRun → start → 3 testResults → complete → в логах Exec `Published TestRunFinished: … summary=3/2/1/0`, в логах Repo `Received TestRunFinished: … summary=Summary[total=3, …]`.

---

## 10. Roadmap — где мы

1. ✅ `tms-common`
2. ✅ `Project` (Repo)
3. ✅ `User` (Repo)
4. ✅ `TestRun` (Exec)
5. ✅ Feign Exec → Repo
6. ✅ Kafka producer + consumer `TestRunFinished`
7. ✅ `TestResult` (Exec) + реальный `summary` ← **текущая точка**
8. ⏭️ **`BugReport` (Exec)** — следующий шаг
9. ⏭️ `ProjectMember`, `TestSuite`, `TestCase` (Repo)
10. ⏭️ Feign-валидация `testCaseId` в `TestResultService.create` (после §9)
11. ⏭️ Flyway V2 `audit_log` в `test_repository` + persistence в `TestRunFinishedListener`
12. ⏭️ Testcontainers / `application-test.yml` / профиль `it`
13. ⏭️ Angular 22 frontend
14. ⏭️ Security (JWT в Gateway, `@PreAuthorize`)
15. ⏭️ Observability (Micrometer + Prometheus + Grafana + Tracing)

### Следующий шаг — `BugReport` (Exec)

Ключевые моменты для следующего слайса (из Конспекта 1 §3 + flyway V1):
- Поля: `id`, `testResultId` (nullable, FK `ON DELETE SET NULL` внутри схемы `test_execution`), `projectId` (UUID ref → Repo), `title`, `description`, `status` (`BugStatus`), `severity` (`BugSeverity`), `priority` (`BugPriority`), `assigneeId` (nullable UUID ref), `reporterId` (NOT NULL UUID ref), `resolvedAt` (nullable `OffsetDateTime`), `createdAt`/`updatedAt`.
- Feign-валидация при create: `projectId` (ProjectClient), `reporterId` (UserClient), `assigneeId` если задан (UserClient), `testResultId` если задан (локально, есть TestResultRepository).
- Бизнес-правило: при переводе в `RESOLVED`/`CLOSED` — проставить `resolvedAt` (если ещё null).
- Soft-delete не применяем (в БД нет `active` на `bug_reports`) — обычный `DELETE`.
- **Не** публикуем Kafka-событие на создание/изменение бага в этом слайсе — событий по багам в контракте пока нет.

---

## 11. Как продолжить в новом чате (copy-paste)

> Продолжаем TMS. **Стек:** Java 21, Spring Boot 4.0.x (milestone), Spring Cloud 2025.1.x (milestone), Kafka 4.0 KRaft, Postgres 18, Angular 22. Maven multi-module `tms`, **пакет `org.learn`**, все дочерние от `com.example.tms:tms`. Порты: 8761 Eureka / 8080 Gateway / 8081 Repo / 8082 Exec / 5432 / 9092 / 8090.
>
> **Инфраструктура рабочая:** Docker Compose — Postgres 18 (схемы `test_repository` + `test_execution`), Kafka 4.0 KRaft, Kafka UI 8090. Flyway V1 применён в обе схемы, `ddl-auto: validate` проходит. Все 4 сервиса стартуют, Eureka видит 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**`.
>
> **Готово:**
> - `tms-common`: 11 enum'ов, `ErrorResponse`, `PageResponse`, `GlobalExceptionHandler`, `KafkaTopics.TEST_RUN_EVENTS`, `TestRunFinishedEvent` (record с `Summary`). Подключён через `@Import(CommonAutoConfiguration.class)`.
> - `Project` (Repo), `User` (Repo, BCrypt, soft-delete), `TestRun` (Exec, lifecycle + Feign-валидация), `TestResult` (Exec, guard «только в IN_PROGRESS», UNIQUE test_run_id+test_case_id, Feign-валидация executedById).
> - **Kafka producer `TestRunFinished`** в `TestRunService.complete()` — `KafkaTemplate<Object,Object>`, `send(...).whenComplete(...)`, key=`testRunId`, summary из `countByTestRunId*`. **Consumer `TestRunFinishedListener`** в Repo (пока только логирует; `@EnableKafka` в `KafkaConsumerConfig`). `spring.json.trusted.packages: org.learn.*` в обоих сервисах.
>
> **Правила проекта (критично):**
> 1. Исключения — сигнатура `(String code, String message)`, коды `SCREAMING_SNAKE_CASE` с префиксом сущности. Словарь: `USER_NOT_FOUND`, `USERNAME_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS`, `PROJECT_NOT_FOUND`, `PROJECT_KEY_ALREADY_EXISTS`, `TEST_RUN_NOT_FOUND`, `TEST_RUN_INVALID_STATE`, `TEST_RESULT_NOT_FOUND`, `TEST_RESULT_ALREADY_EXISTS`, `REPOSITORY_UNAVAILABLE`. **Пополняем словарь ДО кода.**
> 2. Для полей с DB `NOT NULL` + `DEFAULT` — 3 уровня защиты от null: `@Mapping(defaultValue=…)` + `@PrePersist` + проверка в service.
> 3. MapStruct `ignore=true`: `id`, `createdAt`, `updatedAt` (всегда); `passwordHash`, `status` (TestRun), `executedAt` (TestResult); иммутабельные в update-маппере (`projectId`/`createdById`, `testRunId`/`testCaseId`).
> 4. Только `spring-security-crypto`, не `-starter-security`.
> 5. `annotationProcessorPaths` **строго в порядке**: `spring-boot-configuration-processor` → `lombok` → `lombok-mapstruct-binding:0.2.0` → `mapstruct-processor`.
> 6. Cross-service — только UUID, без FK. Валидация через Feign. `@FeignClient(contextId=…)` обязателен. Пути Feign **без** `/api/...`. Catch `FeignException.NotFound` ДО `FeignException`.
> 7. Время в entity — `OffsetDateTime`, не `LocalDateTime`.
> 8. `flyway-database-postgresql` — явно в pom. `ddl-auto: validate` всегда.
> 9. Kafka: топик `test-run-events`, key=`testRunId`, событие `TestRunFinished` (`tms-common.event.TestRunFinishedEvent`). Serializer'ы из авто-конфига, `trusted.packages: org.learn.*`, `@EnableKafka` для consumer.
> 10. Gateway — WebFlux-only, без `starter-web`. `discovery.locator.enabled: false`, `StripPrefix=2`.
> 11. Три места согласования схемы: `hikari.schema`, `hibernate.default_schema`, `flyway.schemas`/`default-schema`.
>
> **Следующий шаг:** слайс **`BugReport`** в `test-execution-service`: entity → repo → mapper → service → controller → DTO + Feign-валидация `projectId`/`reporterId`/`assigneeId` + локальная валидация `testResultId`. Поля: `testResultId` (nullable, FK ON DELETE SET NULL), `projectId` (ref), `title`, `description`, `status`(BugStatus), `severity`(BugSeverity), `priority`(BugPriority), `assigneeId` (nullable ref), `reporterId` (ref, NOT NULL), `resolvedAt` (nullable; проставляется при переходе в RESOLVED/CLOSED). Новые коды: `BUG_REPORT_NOT_FOUND` (404), `TEST_RESULT_NOT_FOUND` уже есть (переиспользуем для локальной валидации). Kafka-события по багам пока не публикуем. Готов формировать `BugReport`.