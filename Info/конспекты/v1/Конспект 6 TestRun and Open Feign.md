# Конспект 6: TestRun + Feign Execution → Repository (готово)

## 1. Итоговое состояние

| Что | Статус |
|---|---|
| Инфраструктура (Docker) | ✅ Postgres 18 + Kafka 4.0 KRaft + Kafka UI 8090 |
| Flyway V1 | ✅ Обе схемы применены, `ddl-auto: validate` проходит |
| 4 сервиса в Eureka | ✅ 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**` |
| Модуль `tms-common` | ✅ DTO, enum'ы, исключения, `GlobalExceptionHandler`, `KafkaTopics`, `TestRunFinishedEvent` |
| Слайс `Project` (Repo) | ✅ Полный CRUD |
| Слайс `User` (Repo) | ✅ Полный CRUD + BCrypt + soft-delete |
| **Слайс `TestRun` (Exec)** | ✅ **Entity, Repo, Mapper, Service, Controller, DTO, lifecycle-переходы** |
| **Feign Exec → Repo** | ✅ **`ProjectClient` / `UserClient`, валидация `projectId` и `createdById` перед create** |
| `ServiceUnavailableException` | ✅ Добавлен в `tms-common` |
| Smoke-тесты | ✅ Проходят (201/404/409/503) |

---

## 2. Стек (не менять)

| Слой | Версия |
|---|---|
| Java | 21 LTS |
| Spring Boot | 4.0.x (milestone) |
| Spring Cloud | 2025.1.x (milestone) |
| Kafka | 4.0 KRaft (без ZooKeeper) |
| PostgreSQL | 18 |
| Flyway | 11.x (managed by Boot) + `flyway-database-postgresql` **явно** |
| Angular | 22.x |
| Node.js | 24 LTS |

**Maven multi-module `tms`**, все дочерние от `com.example.tms:tms`. **Базовый пакет — `org.learn`** (не `com.example.tms` — это отличие от Конспекта 2!).

**Порты:** 8761 Eureka / 8080 Gateway / 8081 Repo / 8082 Exec / 5432 Postgres / 9092 Kafka / 8090 Kafka UI.

---

## 3. Структура модулей

```
tms/
├── pom.xml                          (parent, packaging=pom)
├── tms-common/                      (общие DTO, enum'ы, исключения)
├── eureka-server/                   (8761)
├── api-gateway/                     (8080, WebFlux-only)
├── test-repository-service/         (8081)
└── test-execution-service/          (8082)
```

**Gateway**: WebFlux-only, **без** `spring-boot-starter-web`. Маршруты явные (`discovery.locator.enabled: false`), `StripPrefix=2`.

**Бизнес-сервисы**: одна БД `tms`, **разные схемы** `test_repository` / `test_execution`. Три места согласования схемы: `hikari.schema`, `hibernate.default_schema`, `flyway.schemas`+`default-schema`.

---

## 4. ⚠️ ПРАВИЛА ПРОЕКТА (критично, не повторять ошибок)

### 4.1. Исключения — сигнатура `(String code, String message)`

```java
new ResourceNotFoundException(code, message)   // → 404
new ConflictException(code, message)           // → 409
new BadRequestException(code, message)         // → 400
new ServiceUnavailableException(code, message) // → 503
```

`code` — `SCREAMING_SNAKE_CASE` с префиксом сущности. `message` — для человека.

**Словарь кодов (актуальный):**

| code | status | источник |
|---|---|---|
| `USER_NOT_FOUND` | 404 | Repo: `UserService`; Exec: `TestRunService` (Feign) |
| `USERNAME_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `EMAIL_ALREADY_EXISTS` | 409 | Repo: `UserService` |
| `PROJECT_NOT_FOUND` | 404 | Repo: `ProjectService`; Exec: `TestRunService` (Feign) |
| `PROJECT_KEY_ALREADY_EXISTS` | 409 | Repo: `ProjectService` |
| `TEST_RUN_NOT_FOUND` | 404 | Exec: `TestRunService` |
| `TEST_RUN_INVALID_STATE` | 409 | Exec: `TestRunService` (недопустимый переход статуса) |
| `REPOSITORY_UNAVAILABLE` | 503 | Exec: `TestRunService` (Feign упал не с 404) |

**Правило:** новый слайс → сначала пополняем словарь, потом код.

### 4.2. Три уровня защиты от `null` (для полей с DB `NOT NULL` + `DEFAULT`)

1. **Mapper**: `@Mapping(target="<field>", source="<field>", defaultValue="...")`
2. **Entity**: `@PrePersist void prePersist() { if (field == null) field = <default>; }`
3. **Service**: явная проверка после маппинга

**Исключение:** поля с `@NotNull` в DTO — им хватает только `@NotNull` (валидация отсечёт `null` раньше).

### 4.3. MapStruct — обязательные `ignore=true`

Все поля, управляемые сервисом, должны быть `ignore=true` в мапперах create/update:
- `id`, `createdAt`, `updatedAt` — всегда
- `passwordHash` (User) — всегда, ставится в сервисе
- `status` (TestRun) — всегда, ставится в сервисе
- `projectId`, `createdById` (TestRun) — в **update** маппере (иммутабельные поля)

### 4.4. Security

Только `spring-security-crypto` (BCrypt), **не** `-starter-security`.

### 4.5. Maven / Lombok / MapStruct

`maven-compiler-plugin` с `<annotationProcessorPaths>` **в строгом порядке**:
1. `spring-boot-configuration-processor`
2. `lombok`
3. `lombok-mapstruct-binding` (**0.2.0**)
4. `mapstruct-processor`

Без `annotationProcessorPaths` — MapStruct/Lombok не работают. `<executions>` блок убран.

### 4.6. Автоконфигурация `tms-common`

`AutoConfiguration.imports` **не подхватывается** — в главных классах бизнес-сервисов явный:

```java
@SpringBootApplication
@Import(CommonAutoConfiguration.class)
public class ...Application { }
```

### 4.7. Flyway

`flyway-database-postgresql` — **явная зависимость** в pom. Без неё — `Unsupported Database: PostgreSQL 18.0`.

`ddl-auto: validate` — схему меняет **только** Flyway. V1 — forward-only, без `DROP IF EXISTS`.

### 4.8. Время

Все timestamps в entity — `OffsetDateTime` (под `TIMESTAMPTZ`). **Не** `LocalDateTime`.

### 4.9. Cross-service

Только UUID, **без FK**. Валидация ссылок — на уровне приложения (Feign). FK — только внутри своей схемы.

---

## 5. Enum-значения (строго по Конспекту 1)

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

Продублированы в `CHECK`-констрейнтах БД (V1).

---

## 6. `tms-common` — что внутри

- `enums/*` — все 11 enum'ов из §5
- `dto/ErrorResponse`, `dto/PageResponse`
- `exception/ApiException` (база) + `ResourceNotFoundException`, `ConflictException`, `BadRequestException`, **`ServiceUnavailableException`**
- `exception/GlobalExceptionHandler` (перехватывает `ApiException`, `DataIntegrityViolationException`, `MethodArgumentNotValidException`, `Exception`)
- `consts/KafkaTopics` — `TEST_RUN_EVENTS = "test-run-events"`
- `event/TestRunFinishedEvent` (record)
- `autoconfig/CommonAutoConfiguration`

**Зависимости `tms-common`:** `jakarta.servlet-api` и `spring-tx` со scope **`provided`**.

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

`passwordHash` — BCrypt, `VARCHAR(255)`. В `UserResponse` пароля нет.

### 7.2. `Project` — эндпоинты

POST / GET (paginated+filter) / GET by id / GET by key / PUT / DELETE.

`ownerId` — скалярный `UUID`, **без `@ManyToOne`**.

---

## 8. Execution Service — слайс `TestRun`

### 8.1. Файлы (все под `org.learn.execution.*`)

```
domain/TestRun.java
dto/testrun/TestRunCreateRequest.java
dto/testrun/TestRunUpdateRequest.java
dto/testrun/TestRunResponse.java
mapper/TestRunMapper.java
repository/TestRunRepository.java
service/TestRunService.java
controller/TestRunController.java
client/ProjectClient.java            (@FeignClient, contextId="projectClient")
client/UserClient.java               (@FeignClient, contextId="userClient")
```

### 8.2. Эндпоинты (через Gateway → `/api/execution/test-runs`)

| Метод | Путь | Ответ | Назначение |
|---|---|---|---|
| POST | `/test-runs` | 201 / 404 / 503 | создание (валидирует `projectId`+`createdById` через Feign) |
| GET | `/test-runs?projectId=&status=&page=&size=&sort=` | 200 | пагинация + фильтры |
| GET | `/test-runs/{id}` | 200 / 404 | по UUID |
| PUT | `/test-runs/{id}` | 200 / 404 / 409 | только в статусе CREATED |
| PATCH | `/test-runs/{id}/start` | 200 / 404 / 409 | CREATED → IN_PROGRESS |
| PATCH | `/test-runs/{id}/complete` | 200 / 404 / 409 | IN_PROGRESS → COMPLETED (+ `finishedAt`) |
| PATCH | `/test-runs/{id}/abort` | 200 / 404 / 409 | CREATED\|IN_PROGRESS → ABORTED |
| DELETE | `/test-runs/{id}` | 204 / 404 / 409 | только CREATED |

### 8.3. Матрица переходов

| Текущий | start | complete | abort | update | delete |
|---|---|---|---|---|---|
| CREATED | ✅ IN_PROGRESS | ❌ 409 | ✅ ABORTED | ✅ | ✅ |
| IN_PROGRESS | ❌ 409 | ✅ COMPLETED | ✅ ABORTED | ❌ 409 | ❌ 409 |
| COMPLETED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |
| ABORTED | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 | ❌ 409 |

### 8.4. Feign — детали

- `@FeignClient(name="test-repository-service", contextId="projectClient")` — **`contextId` обязателен**, оба клиента смотрят в один сервис, иначе `BeanDefinitionOverrideException`.
- Метод `checkExists` возвращает `void` — нужен только факт `200 OK`. `404` → `FeignException.NotFound`.
- **Пути в Feign — без `/api/...`** (Feign идёт через Eureka напрямую, не через Gateway; `StripPrefix=2` — только в Gateway).
- Порядок catch: `FeignException.NotFound` (подкласс) → `FeignException` (родитель). Иначе 404 маскируется под 503.
- `@EnableFeignClients(basePackages = "org.learn.execution.client")` в главном классе.
- `spring-cloud-starter-loadbalancer` — должен быть на classpath (обычно транзитивно с openfeign).

### 8.5. Kafka-заготовка

В `TestRunService.complete()` — маркер `// TODO (roadmap): publish TestRunFinishedEvent`, реализации пока нет.

---

## 9. Kafka — соглашения (для следующего шага)

- **Topic:** `test-run-events` (константа `KafkaTopics.TEST_RUN_EVENTS` уже в `tms-common`)
- **Key:** `testRunId` (String)
- **Value:** `TestRunFinishedEvent` (record в `tms-common`)
- **eventType:** `"TestRunFinished"`
- **Структура:** `{ eventType, testRunId, projectId, status, finishedAt, summary:{total,passed,failed,blocked}, triggeredBy }`

Сериализация — `JsonSerializer`/`JsonDeserializer` + `spring.json.trusted.packages: org.learn.*` (в конфиге стоит `com.example.tms.*` — **проверить/поправить при подключении producer!**).

---

## 10. Roadmap — где мы сейчас

1. ✅ `tms-common`
2. ✅ `Project` (Repo)
3. ✅ `User` (Repo)
4. ✅ `TestRun` (Exec)
5. ✅ **Feign Exec → Repo** ← текущая точка
6. ⏭️ **Kafka producer `TestRunFinished`** в `TestRunService.complete()`
7. ⏭️ **Kafka consumer** в Repository (аудит/нотификации)
8. ⏭️ `TestResult` (Exec) — и **дозаполнение `summary` в `TestRunFinishedEvent`**
9. ⏭️ `BugReport` (Exec)
10. ⏭️ `ProjectMember`, `TestSuite`, `TestCase` (Repo)
11. ⏭️ Тесты: Testcontainers, `application-test.yml`, профиль `it`
12. ⏭️ Angular 22 frontend
13. ⏭️ Security (JWT в Gateway, `@PreAuthorize`)
14. ⏭️ Observability (Micrometer + Prometheus + Grafana + Tracing)

### Открытое решение по порядку 6/8

`summary` в `TestRunFinishedEvent` строится по `test_results`, а `TestResult` ещё нет. Два варианта:
- **A)** Producer сейчас, `summary` = нули + TODO. Дозаполним после §8 без ломки контракта (additive).
- **B)** Сначала §8 `TestResult`, потом §6/7 Kafka — данные полные с первого дня.

---

## 11. Быстрые проверки (для дебага)

```bash
# Eureka: 3 приложения UP
curl -s http://localhost:8761/eureka/apps | grep -E '<name>|<status>'

# Gateway + маршруты
curl -s http://localhost:8080/api/repository/actuator/health   # UP
curl -s http://localhost:8080/api/execution/actuator/health    # UP

# Flyway history
docker exec -it tms-postgres psql -U tms -d tms -c \
  "SELECT 'repo',version,success FROM test_repository.flyway_schema_history
   UNION ALL SELECT 'exec',version,success FROM test_execution.flyway_schema_history;"

# TestRun create → 201 (Feign валидирует projectId + createdById)
# TestRun create с рандомным projectId → 404 PROJECT_NOT_FOUND
# TestRun create при остановленном Repo → 503 REPOSITORY_UNAVAILABLE
```

---

## 12. Первое сообщение в новый чат (copy-paste)

> Продолжаем TMS. **Стек:** Java 21, Spring Boot 4.0.x (milestone), Spring Cloud 2025.1.x (milestone), Kafka 4.0 KRaft, Postgres 18, Angular 22. Maven multi-module `tms`, **пакет `org.learn`**, все дочерние от `com.example.tms:tms`. Порты: 8761 Eureka / 8080 Gateway / 8081 Repo / 8082 Exec / 5432 / 9092 / 8090.
>
> **Инфраструктура рабочая:** Docker Compose — Postgres 18 (схемы `test_repository` + `test_execution`), Kafka 4.0 KRaft, Kafka UI 8090. Flyway V1 применён в обе схемы, `ddl-auto: validate` проходит. Все 4 сервиса стартуют, Eureka видит 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**`.
>
> **Готово:**
> - `tms-common`: все 11 enum'ов, `ErrorResponse`, `PageResponse`, `GlobalExceptionHandler`, `KafkaTopics.TEST_RUN_EVENTS`, `TestRunFinishedEvent` (record). Подключён через `@Import(CommonAutoConfiguration.class)` в главных классах.
> - `Project` (Repo), `User` (Repo, BCrypt, soft-delete), `TestRun` (Exec, lifecycle-переходы + Feign-валидация `projectId`/`createdById`).
>
> **Правила проекта (критично, не повторять ошибок):**
> 1. Исключения — сигнатура `(String code, String message)`, коды `SCREAMING_SNAKE_CASE` с префиксом сущности. Словарь пополняем **до** кода. Новый код сейчас: `ServiceUnavailableException` → 503 `REPOSITORY_UNAVAILABLE`.
> 2. Поля с DB `NOT NULL` + `DEFAULT` — **три уровня защиты от null**: `@Mapping(defaultValue=…)` + `@PrePersist` + явная проверка в service.
> 3. MapStruct: `ignore=true` для `id`, `passwordHash`, `status` (TestRun), `createdAt`, `updatedAt`; иммутабельные поля (`projectId`, `createdById`) — `ignore=true` в update-маппере.
> 4. Только `spring-security-crypto`, не `-starter-security`.
> 5. `maven-compiler-plugin` — `annotationProcessorPaths` **строго в порядке**: `spring-boot-configuration-processor` → `lombok` → `lombok-mapstruct-binding:0.2.0` → `mapstruct-processor`. Без этого MapStruct/Lombok молча не работают.
> 6. Cross-service — только UUID, без FK. Валидация — Feign. `@FeignClient(contextId=…)` обязателен, иначе конфликт двух клиентов к одному сервису. Feign-пути **без** `/api/...` (идут через Eureka, не через Gateway). Catch `FeignException.NotFound` **до** `FeignException`.
> 7. Время в entity — `OffsetDateTime`, не `LocalDateTime`.
> 8. `flyway-database-postgresql` — явная зависимость в pom.
> 9. Enum-значения — строго по списку (UserRole/ProjectStatus/ProjectRole/TestCasePriority/TestCaseType/TestCaseStatus/TestRunStatus/TestResultStatus/BugStatus/BugSeverity/BugPriority). Дублируются в `CHECK` БД.
> 10. Kafka: топик `test-run-events`, ключ = `testRunId`, событие `TestRunFinished` (структура в `tms-common.event.TestRunFinishedEvent`).
>
> **Следующий шаг:** Kafka producer `TestRunFinished` в `TestRunService.complete()` + consumer в Repository. **Перед подключением producer — поправить `spring.json.trusted.packages: org.learn.*`** в `application.yml` бизнес-сервисов (сейчас там `com.example.tms.*` — не совпадает с реальным пакетом). Затем `TestResult` (Exec) и дозаполнение `summary` в событии. Скажи, какой порядок предпочитаешь (producer сейчас с нулевым summary — вариант A, или сначала TestResult — вариант B), и выдай код.