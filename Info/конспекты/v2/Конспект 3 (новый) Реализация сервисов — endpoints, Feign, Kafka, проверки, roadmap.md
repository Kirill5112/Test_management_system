# Конспект 3 (новый): Реализация сервисов — endpoints, Feign, Kafka, проверки, roadmap

> Объединённая версия реализации из Конспектов 4–8. Финальные endpoints, Feign-клиенты, Kafka producer/consumer, E2E smoke, roadmap. Все 7 сущностей реализованы. Убраны промежуточные состояния — оставлена только актуальная версия.

---

## 1. Итоговое состояние проекта

| Что | Статус |
|---|---|
| Инфраструктура (Docker) | ✅ Postgres 18 + Kafka 4.0 KRaft + Kafka UI 8090 |
| Flyway V1 | ✅ Обе схемы применены, `ddl-auto: validate` проходит |
| 4 сервиса в Eureka | ✅ 3 UP, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**` |
| `tms-common` | ✅ DTO, 11 enum'ов + **`ErrorCode`**, исключения (4 шт.), `GlobalExceptionHandler`, `KafkaTopics`, `TestRunFinishedEvent` |
| **Все 7 сущностей из Конспекта 1** | ✅ Доменная модель исчерпана |
| Kafka producer/consumer `TestRunFinished` | ✅ Producer в Exec, consumer в Repo (пока только логирует) |
| Feign-клиенты (Exec → Repo) | ✅ `ProjectClient`, `UserClient`, `TestCaseClient` |
| Smoke-тесты (Postman/curl) | ✅ Проходят |

**Базовая точка:** работает end-to-end поток Project → User → TestSuite → TestCase → TestRun → TestResult → BugReport → Kafka event → consumer log.

---

## 2. Repository Service — реализованные слайсы

Базовый пакет: `org.learn.repository.*`
Схема БД: `test_repository`

### 2.1. `User` (`/users`)

**Файлы** (под `org.learn.repository.`):
```
domain/User.java
dto/user/UserCreateRequest.java       (active: Boolean, @Builder.Default=true)
dto/user/UserUpdateRequest.java       (active: @NotNull)
dto/user/ChangePasswordRequest.java   (password: @NotBlank, 8..100)
dto/user/UserResponse.java            (без passwordHash)
mapper/UserMapper.java                (MapStruct, defaultValue для active)
repository/UserRepository.java        (existsBy*AndIdNot, findByUsername/Email)
service/UserService.java              (двухаргументные исключения)
controller/UserController.java
config/PasswordConfig.java            (BCryptPasswordEncoder @Bean)
```

**Эндпоинты (через Gateway → `/api/repository/users`):**

| Метод | Путь | Ответ | Назначение |
|---|---|---|---|
| POST | `/users` | 201 + `Location` | создание |
| GET | `/users?page=&size=&sort=` | 200 | пагинация (`@PageableDefault(size=20, sort="createdAt")`) |
| GET | `/users/{id}` | 200 / 404 | по UUID |
| GET | `/users/by-username/{username}` | 200 / 404 | по username |
| PUT | `/users/{id}` | 200 / 404 / 409 | полная замена (без пароля) |
| PATCH | `/users/{id}/password` | 204 / 404 | смена пароля |
| DELETE | `/users/{id}` | 204 / 404 | **soft-delete** (`active=false`) |

**Ключевые решения:**
- `passwordHash` — BCrypt, `VARCHAR(255)`, хэш 60 символов укладывается.
- Soft-delete обязателен: `users.id` держат FK из `projects.owner_id`, `test_cases.created_by_id`, `project_members.user_id`. Hard-delete невозможен.
- `passwordHash` **никогда не отдаётся** наружу — в `UserResponse` поля нет.
- Хэш ставится **только в сервисе** через `passwordEncoder.encode(...)`, никогда в маппере.

### 2.2. `Project` (`/projects`)

**Файлы:**
```
domain/Project.java
dto/project/{ProjectCreateRequest, ProjectUpdateRequest, ProjectResponse}
mapper/ProjectMapper.java
repository/ProjectRepository.java     (existsByKey, JpaSpecificationExecutor)
service/ProjectService.java
controller/ProjectController.java
```

**Эндпоинты (через Gateway → `/api/repository/projects`):**

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/projects` | 201 / 409 / 503 |
| GET | `/projects?status=&ownerId=&page=&size=&sort=` | 200 |
| GET | `/projects/{id}` | 200 / 404 |
| GET | `/projects/by-key/{key}` | 200 / 404 |
| PUT | `/projects/{id}` | 200 / 404 / 409 |
| DELETE | `/projects/{id}` | 204 / 404 |

**Ключевые решения:**
- `ownerId` — скалярный `UUID`, **без `@ManyToOne`**. Валидация владельца через FK на БД: при create с несуществующим `ownerId` → `DataIntegrityViolationException` → `GlobalExceptionHandler` → **409**.
- Валидация `key` через `@Pattern` (например, `^[A-Z][A-Z0-9_]{1,19}$`).
- Проверка дубликата `key` в сервисе — `existsByKey` → `409 PROJECT_KEY_EXISTS`.

### 2.3. `ProjectMember` (`/project-members`)

**Файлы:**
```
domain/ProjectMemberId.java           (@Embeddable, implements Serializable, @EqualsAndHashCode)
domain/ProjectMember.java             (@EmbeddedId)
dto/projectmember/{ProjectMemberCreateRequest, ProjectMemberUpdateRequest, ProjectMemberResponse}
mapper/ProjectMemberMapper.java       (@Mapping(target="id", expression="java(new ProjectMemberId(request.getProjectId(), request.getUserId()))"))
repository/ProjectMemberRepository.java  (@Query для existsBy*, см. Документ 2 §9.1)
service/ProjectMemberService.java     (валидирует projectId/userId локально, ловит дубликат)
controller/ProjectMemberController.java
```

**Эндпоинты (через Gateway → `/api/repository/project-members`):**

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/project-members` | 201 + `Location` / 404 / 409 |
| GET | `/project-members?projectId=&userId=&page=&size=&sort=` | 200 |
| GET | `/project-members/{projectId}/{userId}` | 200 / 404 |
| PUT | `/project-members/{projectId}/{userId}` | 200 / 404 |
| DELETE | `/project-members/{projectId}/{userId}` | 204 / 404 |

**Ключевые решения:**
- **Composite PK** (`projectId`, `user_id`). Никаких сурогатных id.
- Дубликат → `409 PROJECT_MEMBER_ALREADY_EXISTS`.
- `projectId` / `userId` валидируются локально (Repo видит обе таблицы) — не через Feign.

### 2.4. `TestSuite` (`/test-suites`)

**Файлы:**
```
domain/TestSuite.java                 (parentSuiteId — скалярный UUID, self-FK)
dto/testsuite/{TestSuiteCreateRequest, TestSuiteUpdateRequest, TestSuiteResponse}
mapper/TestSuiteMapper.java
repository/TestSuiteRepository.java   (existsByProjectIdAndName, JpaSpecificationExecutor)
service/TestSuiteService.java         (validateParentSuite — exists/same project/not self/no cycle)
controller/TestSuiteController.java
```

**Эндпоинты (через Gateway → `/api/repository/test-suites`):**

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/test-suites` | 201 / 404 / 409 / 400 |
| GET | `/test-suites?projectId=&parentSuiteId=&page=&size=&sort=` | 200 |
| GET | `/test-suites/{id}` | 200 / 404 |
| PUT | `/test-suites/{id}` | 200 / 404 / 409 / 400 |
| DELETE | `/test-suites/{id}` | 204 / 404 / 409 |

**Ключевые решения — `validateParentSuite`:**
1. `parentSuiteId` существует → `404 TEST_SUITE_NOT_FOUND`.
2. `parentSuiteId` принадлежит **тому же `projectId`**, что и suite → `400 TEST_SUITE_INVALID_PARENT`.
3. `parentSuiteId != self.id` → `400 TEST_SUITE_INVALID_PARENT`.
4. **No cycle:** walk up по `parentSuiteId` до root, не встретить `self.id` → `400 TEST_SUITE_INVALID_PARENT`.

Валидация `projectId` при create — локально (`ProjectRepository.existsById`).

### 2.5. `TestCase` (`/test-cases`)

**Файлы:**
```
domain/TestCaseStep.java              (record: Integer order, String action, String expected)
domain/TestCase.java                  (steps: @JdbcTypeCode(SqlTypes.JSON) List<TestCaseStep>)
dto/testcase/{TestCaseCreateRequest, TestCaseUpdateRequest, TestCaseResponse}
mapper/TestCaseMapper.java
repository/TestCaseRepository.java    (existsBySuiteId, JpaSpecificationExecutor)
service/TestCaseService.java          (валидирует suiteId + createdById локально)
controller/TestCaseController.java
```

**Эндпоинты (через Gateway → `/api/repository/test-cases`):**

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/test-cases` | 201 / 404 / 409 |
| GET | `/test-cases?suiteId=&priority=&type=&status=&page=&size=&sort=` | 200 |
| GET | `/test-cases/{id}` | 200 / 404 |
| PUT | `/test-cases/{id}` | 200 / 404 / 409 |
| DELETE | `/test-cases/{id}` | 204 / 404 / 409 |

**Ключевые решения:**
- `steps` — **JSONB** через `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`.
- `createdById` — валидируется **локально** (`UserRepository.existsById`).
- `suiteId` — валидируется **локально** (`TestSuiteRepository.existsById`).
- `status` в create — ставится в сервисе (`DRAFT` по умолчанию, `ignore=true` в маппере).

### 2.6. Kafka consumer `TestRunFinishedListener`

**Файл:** `org.learn.repository.kafka.TestRunFinishedListener`

```java
@Component
public class TestRunFinishedListener {
    @KafkaListener(topics = KafkaTopics.TEST_RUN_EVENTS, groupId = "test-repository-service")
    public void onTestRunFinished(TestRunFinishedEvent event) {
        log.info("Received TestRunFinished: testRunId={}, summary={}",
                 event.testRunId(), event.summary());
        // TODO (roadmap): persist to audit_log (Flyway V2)
    }
}
```

`@EnableKafka` — в `org.learn.repository.config.KafkaConsumerConfig`.

**Сейчас только логирует.** Persistence в `audit_log` — следующий шаг (Flyway V2).

---

## 3. Execution Service — реализованные слайсы

Базовый пакет: `org.learn.execution.*`
Схема БД: `test_execution`

### 3.1. `TestRun` (`/test-runs`)

**Файлы:**
```
domain/TestRun.java
dto/testrun/{TestRunCreateRequest, TestRunUpdateRequest, TestRunResponse}
mapper/TestRunMapper.java             (ignore: id, status, createdAt, updatedAt; в update — projectId, createdById)
repository/TestRunRepository.java     (JpaSpecificationExecutor)
service/TestRunService.java           (+ Kafka producer, Feign валидация)
controller/TestRunController.java
client/ProjectClient.java             (@FeignClient contextId="projectClient")
client/UserClient.java                (@FeignClient contextId="userClient")
```

**Эндпоинты (через Gateway → `/api/execution/test-runs`):**

| Метод | Путь | Ответ | Назначение |
|---|---|---|---|
| POST | `/test-runs` | 201 / 404 / 503 | создание + Feign-валидация `projectId` + `createdById` |
| GET | `/test-runs?projectId=&status=&page=&size=&sort=` | 200 | пагинация + фильтры |
| GET | `/test-runs/{id}` | 200 / 404 | по UUID |
| PUT | `/test-runs/{id}` | 200 / 404 / 409 | только в статусе CREATED |
| PATCH | `/test-runs/{id}/start` | 200 / 404 / 409 | CREATED → IN_PROGRESS |
| PATCH | `/test-runs/{id}/complete` | 200 / 404 / 409 | IN_PROGRESS → COMPLETED (+ `finishedAt` + **Kafka publish**) |
| PATCH | `/test-runs/{id}/abort` | 200 / 404 / 409 | CREATED\|IN_PROGRESS → ABORTED |
| DELETE | `/test-runs/{id}` | 204 / 404 / 409 | только CREATED |

**Матрица переходов** — см. Документ 2 §14.1.

**Валидация при create:**
- `projectId` — Feign `ProjectClient.checkExists` → `404 PROJECT_NOT_FOUND` / `503 REPOSITORY_UNAVAILABLE`.
- `createdById` — Feign `UserClient.checkExists` → `404 USER_NOT_FOUND` / `503 REPOSITORY_UNAVAILABLE`.

**Логика `complete()`:**
1. Валидирует, что `status == IN_PROGRESS`, иначе `409 TEST_RUN_INVALID_STATE`.
2. Ставит `status = COMPLETED`, `finishedAt = OffsetDateTime.now()`.
3. Считает `summary` через `testResultRepository.countByTestRunId*` (4 запроса: total, passed, failed, blocked). **`total` включает SKIPPED.**
4. Публикует `TestRunFinishedEvent` в Kafka:
   ```java
   kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, testRunId.toString(), event)
       .whenComplete(...);
   ```
   Не блокирует HTTP-поток. Ошибки только логируются.

### 3.2. `TestResult` (`/test-results`)

**Файлы:**
```
domain/TestResult.java
dto/testresult/{TestResultCreateRequest, TestResultUpdateRequest, TestResultResponse}
mapper/TestResultMapper.java          (ignore: id, executedAt, createdAt, updatedAt; в update — testRunId, testCaseId)
repository/TestResultRepository.java  (countByTestRunId*, existsByTestRunIdAndTestCaseId, JpaSpecificationExecutor)
service/TestResultService.java        (guard IN_PROGRESS, Feign testCaseId + executedById)
controller/TestResultController.java
client/TestCaseClient.java            (@FeignClient contextId="testCaseClient")
```

**Эндпоинты (через Gateway → `/api/execution/test-results`):**

| Метод | Путь | Ответ | Назначение |
|---|---|---|---|
| POST | `/test-results` | 201 / 404 / 409 / 503 | создание (guard IN_PROGRESS) |
| GET | `/test-results?testRunId=&status=&page=&size=&sort=` | 200 / 404 | пагинация + фильтры |
| GET | `/test-results/{id}` | 200 / 404 | по UUID |
| PUT | `/test-results/{id}` | 200 / 404 / 409 | только в IN_PROGRESS |
| DELETE | `/test-results/{id}` | 204 / 404 / 409 | только в IN_PROGRESS |

**Валидация при create/update/delete:**
- `testRunId` — прогон **должен быть `IN_PROGRESS`** → иначе `409 TEST_RUN_INVALID_STATE`.
- `testRunId` не существует → `404 TEST_RUN_NOT_FOUND`.
- `testCaseId` — Feign `TestCaseClient.checkExists` → `404 TEST_CASE_NOT_FOUND` / `503 REPOSITORY_UNAVAILABLE`.
- `executedById` (опц.) — если задан, Feign `UserClient.checkExists` → `404 USER_NOT_FOUND` / `503 REPOSITORY_UNAVAILABLE`.
- **UNIQUE (test_run_id, test_case_id)** → `409 TEST_RESULT_ALREADY_EXISTS`.
- `executedAt` ставит **сервис** (`OffsetDateTime.now()`) при create и update.

**`total` в summary включает SKIPPED** — учтено в `countByTestRunId` (все статусы).

### 3.3. `BugReport` (`/bug-reports`)

**Файлы:**
```
domain/BugReport.java
dto/bugreport/{BugReportCreateRequest, BugReportUpdateRequest, BugReportResponse}
mapper/BugReportMapper.java           (ignore: id, status(create), resolvedAt, createdAt, updatedAt; в update — projectId, reporterId, testResultId)
repository/BugReportRepository.java   (JpaSpecificationExecutor)
service/BugReportService.java         (spec-фильтрация, resolvedAt при RESOLVED/CLOSED)
controller/BugReportController.java
```

**Эндпоинты (через Gateway → `/api/execution/bug-reports`):**

| Метод | Путь | Ответ |
|---|---|---|
| POST | `/bug-reports` | 201 / 404 / 503 |
| GET | `/bug-reports?projectId=&status=&severity=&assigneeId=&reporterId=&page=&size=&sort=` | 200 |
| GET | `/bug-reports/{id}` | 200 / 404 |
| PUT | `/bug-reports/{id}` | 200 / 404 / 409 |
| DELETE | `/bug-reports/{id}` | 204 / 404 |

**Валидация при create:**
- `projectId` — Feign `ProjectClient` → `404 PROJECT_NOT_FOUND` / `503 REPOSITORY_UNAVAILABLE`.
- `reporterId` — Feign `UserClient` → `404 USER_NOT_FOUND` / `503 REPOSITORY_UNAVAILABLE`.
- `assigneeId` (опц.) — Feign `UserClient` если задан.
- `testResultId` (опц.) — **локально** `testResultRepository.existsById` → `404 TEST_RESULT_NOT_FOUND` (TestResult в той же схеме `test_execution`).

**Бизнес-правила:**
- При create — `status = OPEN`, `resolvedAt = null` (`ignore=true` в маппере).
- При переводе в `RESOLVED` / `CLOSED` — проставить `resolvedAt = OffsetDateTime.now()` если ещё `null`.
- При возврате из `RESOLVED`/`CLOSED` в `OPEN`/`IN_PROGRESS` — `resolvedAt` **не сбрасывается** (история).
- Soft-delete **не применяется** (в схеме нет `active`) — обычный `DELETE`.
- **Kafka-события по багам не публикуем** — в контракте пока нет.

---

## 4. Feign-клиенты (Exec → Repo)

Все в `org.learn.execution.client`. `@EnableFeignClients(basePackages = "org.learn.execution.client")` в главном классе.

| Клиент | `contextId` | Endpoint | Используется в |
|---|---|---|---|
| `ProjectClient` | `projectClient` | `GET /projects/{id}` → void | `TestRunService.create`, `BugReportService.create` |
| `UserClient` | `userClient` | `GET /users/{id}` → void | `TestRunService.create`, `TestResultService.create`, `BugReportService.create` |
| `TestCaseClient` | `testCaseClient` | `GET /test-cases/{id}` → void | `TestResultService.create` |

**Правила (см. Документ 2 §7):**
- `contextId` обязателен — без него `BeanDefinitionOverrideException` при старте.
- Пути **без** `/api/...` — Feign идёт через Eureka, не Gateway.
- `checkExists` возвращает `void` — нужен только `200 OK`.
- **Catch `FeignException.NotFound` ДО `FeignException`** — иначе 404 маскируется под 503.

---

## 5. Kafka — producer/consumer

### 5.1. Producer (в `TestRunService.complete()`)

```java
var summary = new TestRunFinishedEvent.Summary(
    (int) testResultRepository.countByTestRunId(id),
    (int) testResultRepository.countByTestRunIdAndStatus(id, PASSED),
    (int) testResultRepository.countByTestRunIdAndStatus(id, FAILED),
    (int) testResultRepository.countByTestRunIdAndStatus(id, BLOCKED)
);

var event = new TestRunFinishedEvent(
    "TestRunFinished",
    testRun.getId(),
    testRun.getProjectId(),
    testRun.getStatus().name(),
    testRun.getFinishedAt(),
    summary,
    testRun.getCreatedById()
);

kafkaTemplate.send(KafkaTopics.TEST_RUN_EVENTS, testRun.getId().toString(), event)
    .whenComplete((result, ex) -> {
        if (ex != null) log.error("Failed to publish TestRunFinished: {}", testRun.getId(), ex);
        else log.info("Published TestRunFinished: {} summary={}", testRun.getId(), summary);
    });
```

**Не создавать кастомный `KafkaProducerConfig`** — хватает авто-конфига Boot.

### 5.2. Consumer (в Repo: `TestRunFinishedListener`)

См. §2.6. Пока только `log.info(...)`, persistence в `audit_log` — roadmap.

### 5.3. Проверка Kafka

```bash
docker exec -it tms-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group test-repository-service
# LAG = 0 после обработки
```

---

## 6. E2E smoke — полный сценарий

```bash
# 1) User
curl -i -X POST http://localhost:8080/api/repository/users \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","email":"bob@example.com","password":"S3cretPass!","role":"TESTER"}'
# → 201, active=true, passwordHash не в ответе. Сохранить id → OWNER_ID

# 2) Project (с реальным ownerId — иначе 409)
curl -i -X POST http://localhost:8080/api/repository/projects \
  -H "Content-Type: application/json" \
  -d '{"key":"DEMO","name":"Demo","ownerId":"<OWNER_ID>","status":"ACTIVE"}'
# → 201. Сохранить id → PROJECT_ID

# 3) TestSuite
curl -i -X POST http://localhost:8080/api/repository/test-suites \
  -H "Content-Type: application/json" \
  -d '{"projectId":"<PROJECT_ID>","name":"Smoke","description":null}'
# → 201. Сохранить id → SUITE_ID

# 4) TestCase (JSONB steps)
curl -i -X POST http://localhost:8080/api/repository/test-cases \
  -H "Content-Type: application/json" \
  -d '{"suiteId":"<SUITE_ID>","title":"Login OK","priority":"HIGH","type":"MANUAL",
       "steps":[{"order":1,"action":"Открыть логин","expected":"Страница загружена"}],
       "createdById":"<OWNER_ID>"}'
# → 201. Сохранить id → CASE_ID

# 5) TestRun (Feign валидирует projectId + createdById)
curl -i -X POST http://localhost:8080/api/execution/test-runs \
  -H "Content-Type: application/json" \
  -d '{"projectId":"<PROJECT_ID>","name":"Run #1","createdById":"<OWNER_ID>","environment":"local"}'
# → 201. Сохранить id → RUN_ID

# 6) start
curl -i -X PATCH http://localhost:8080/api/execution/test-runs/<RUN_ID>/start
# → 200, status=IN_PROGRESS

# 7) TestResults (guard IN_PROGRESS + Feign testCaseId)
curl -i -X POST http://localhost:8080/api/execution/test-results \
  -H "Content-Type: application/json" \
  -d '{"testRunId":"<RUN_ID>","testCaseId":"<CASE_ID>","status":"PASSED"}'
# → 201. (повтор с тем же testCaseId → 409 TEST_RESULT_ALREADY_EXISTS)

# 8) BugReport (Feign projectId+reporterId + локально testResultId)
curl -i -X POST http://localhost:8080/api/execution/bug-reports \
  -H "Content-Type: application/json" \
  -d '{"projectId":"<PROJECT_ID>","title":"Login broken","severity":"MAJOR",
       "priority":"HIGH","reporterId":"<OWNER_ID>",
       "testResultId":"<RESULT_ID>"}'
# → 201, status=OPEN, resolvedAt=null

# 9) BugReport → RESOLVED (resolvedAt проставится автоматически)
curl -i -X PUT http://localhost:8080/api/execution/bug-reports/<BUG_ID> \
  -H "Content-Type: application/json" \
  -d '{"title":"Login broken","severity":"MAJOR","priority":"HIGH","status":"RESOLVED"}'

# 10) complete — публикует Kafka event с реальным summary
curl -i -X PATCH http://localhost:8080/api/execution/test-runs/<RUN_ID>/complete
# → 200, status=COMPLETED, finishedAt

# Логи Exec:
#   Published TestRunFinished: <RUN_ID> summary=Summary[total=N, passed=..., failed=..., blocked=...]
# Логи Repo:
#   Received TestRunFinished: testRunId=<RUN_ID>, summary=Summary[total=N, ...]
```

---

## 7. Опорные команды проверки

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

# Таблицы
docker exec -it tms-postgres psql -U tms -d tms -c "\dt test_repository.*"
docker exec -it tms-postgres psql -U tms -d tms -c "\dt test_execution.*"

# Kafka consumer lag
docker exec -it tms-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group test-repository-service

# JSONB steps (визуальная проверка)
docker exec -it tms-postgres psql -U tms -d tms \
  -c "SELECT id, title, steps FROM test_repository.test_cases LIMIT 1;"

# BCrypt hash
docker exec -it tms-postgres psql -U tms -d tms \
  -c "SELECT username, active, length(password_hash) FROM test_repository.users;"
# hash_len ≈ 60, active не NULL

# Negative-тесты
# TestRun create с рандомным projectId → 404 PROJECT_NOT_FOUND
# TestRun create при остановленном Repo → 503 REPOSITORY_UNAVAILABLE
# TestResult create при Run != IN_PROGRESS → 409 TEST_RUN_INVALID_STATE
# TestResult create с рандомным testCaseId → 404 TEST_CASE_NOT_FOUND
```

---

## 8. Доменная модель — что ещё НЕ сделано по соглашениям

Не реализовано, но **не забыть** (не блокирует текущий E2E):

- `ProjectMember`, `TestSuite`, `TestCase` в Repository — **сделано**.
- Flyway V2 `audit_log` в `test_repository` — **следующий шаг**.
- Feign `TestCaseClient` в `TestResultService` — **сделано**.
- Feign `TestCaseClient.checkExists` для `TestCase.suiteId` — **не нужно** (`TestSuite` в той же схеме).
- Soft-delete для `BugReport` — **не применяется** (в схеме нет `active`).

---

## 9. Roadmap — где мы сейчас

| # | Шаг | Статус |
|---|---|---|
| 1 | `tms-common` + `ErrorCode` | ✅ |
| 2 | `Project`, `User` (Repo) | ✅ |
| 3 | `TestRun`, `TestResult`, `BugReport` (Exec) | ✅ |
| 4 | Kafka producer/consumer `TestRunFinished` | ✅ |
| 5 | `ProjectMember`, `TestSuite`, `TestCase` (Repo) | ✅ |
| 6 | Feign `testCaseId` в `TestResultService.create` | ✅ |
| 7 | **Flyway V2 `audit_log`** в `test_repository` + persistence в `TestRunFinishedListener` | ⏭️ **следующий шаг** |
| 8 | **Testcontainers** + `application-test.yml` + профиль `it` + интеграционные тесты | ⏭️ |
| 9 | **Angular 22 frontend** (скелет + экраны Projects, TestRuns, TestResults) | ⏭️ |
| 10 | **Security:** JWT в Gateway + `@PreAuthorize` в сервисах | ⏭️ |
| 11 | **Observability:** Micrometer + Prometheus + Grafana + Tracing | ⏭️ |

**Текущая точка:** все 7 сущностей + Feign + Kafka работают end-to-end. Следующий шаг — Flyway V2 `audit_log` + persistence consumer'а.

---

## 10. Следующий шаг — детали Flyway V2 `audit_log`

**Цель:** заменить `log.info(...)` в `TestRunFinishedListener` на `auditLogRepository.save(...)`.

**Flyway V2 в `test-repository-service/src/main/resources/db/migration/V2__audit_log.sql`:**

Таблица `test_repository.audit_log` (черновые поля):
- `id` UUID PK
- `event_type` VARCHAR(50) NOT NULL — `TestRunFinished`
- `aggregate_id` UUID NOT NULL — `testRunId`
- `project_id` UUID NOT NULL (ref)
- `payload` JSONB NOT NULL — весь `TestRunFinishedEvent`
- `received_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()

**Entity `AuditLog`** — `@JdbcTypeCode(SqlTypes.JSON)` для `payload`, `OffsetDateTime receivedAt`.

**Consumer** — `auditLogRepository.save(...)` вместо `log.info`. Идемпотентность: `UNIQUE(event_type, aggregate_id)` (опционально — если событие может прийти дважды).

**Не забыть:**
- `@Transactional` на методе listener'а.
- Если ошибка сохранения — log + не падать (Kafka retry-логика — отдельная задача).

---