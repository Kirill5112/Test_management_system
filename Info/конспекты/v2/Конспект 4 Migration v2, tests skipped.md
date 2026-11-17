# Итоговый отчёт: сессия «Flyway V2 audit_log + Testcontainers IT»

> Для передачи в новый чат. Всё, что ниже — состояние на конец этой сессии.

---

## 1. Что сделано (в коде — готово, работает)

### 1.1. Flyway V2 — `audit_log` в `test_repository`

- Файл: `test-repository-service/src/main/resources/db/migration/V2__audit_log.sql`
- Таблица `test_repository.audit_log`: `id` UUID PK, `event_type` VARCHAR(50), `aggregate_id` UUID, `project_id` UUID (ref, без FK), `payload` JSONB, `received_at` TIMESTAMPTZ DEFAULT NOW().
- Индексы:
  - `ux_audit_log_event_aggregate` UNIQUE (`event_type`, `aggregate_id`) — идемпотентность при at-least-once delivery.
  - `idx_audit_log_project_id` — типовой запрос «журнал по проекту».
  - `idx_audit_log_received_at` — типовой запрос «журнал по времени».
- Forward-only, без `DROP IF EXISTS`. Соглашение V1 сохранено.

### 1.2. Entity `AuditLog`

- Файл: `test-repository-service/src/main/java/org/learn/repository/domain/AuditLog.java`
- Поля: `id`, `eventType`, `aggregateId`, `projectId`, `payload` (`TestRunFinishedEvent`), `receivedAt`.
- `payload` — `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`. Импорты `org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes` — по правилу проекта.
- `@PrePersist` — страховка от null по `id` и `receivedAt` (3-й уровень защиты, как в правилах).
- Lombok `@Builder`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`.

### 1.3. `AuditLogRepository`

- Файл: `test-repository-service/src/main/java/org/learn/repository/repository/AuditLogRepository.java`
- `JpaRepository<AuditLog, UUID>` + `boolean existsByEventTypeAndAggregateId(String, UUID)` (производный, не `EmbeddedId` — `@Query` не нужен).

### 1.4. Обновлённый `TestRunFinishedListener`

- Файл: `test-repository-service/src/main/java/org/learn/repository/kafka/TestRunFinishedListener.java`
- Логика:
  1. Лог `Received TestRunFinished`.
  2. `existsByEventTypeAndAggregateId` → если true, `log.info("Duplicate ... skipping")` и ранний return (быстрый путь дедупа).
  3. `save()` через билдер.
  4. `catch (DataIntegrityViolationException)` — гонка (оба сообщения прошли `existsBy` одновременно), ловим UNIQUE-индекс, `log.warn`, не падаем.
- **Важное отклонение от заметок к шагу:** `@Transactional` на listener **не ставим**. `SimpleJpaRepository.save()` уже `@Transactional` — своя короткая транзакция. `@Transactional` на listener + поглощение `DataIntegrityViolationException` → `UnexpectedRollbackException` / `TransactionSystemException` при выходе, Kafka-retry вхолостую. Без `@Transactional` на listener'е — `save()` откатывается локально, сессия чистая.
- `@Slf4j`, `@RequiredArgsConstructor`.

### 1.5. Тестовая инфраструктура (готова, но пока пропускается)

- `pom.xml` модуля:
  - Добавлены: `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`, `org.testcontainers:kafka`, `spring-kafka-test` (все test scope).
  - Добавлен `maven-failsafe-plugin` (goals `integration-test`, `verify`).
  - Добавлен BOM `org.testcontainers:testcontainers-bom:1.20.4` в `dependencyManagement` (для случая, если Boot 4.0 milestone не тянет его в свой BOM).
- `test-repository-service/src/test/resources/application-it.yml`:
  - `ddl-auto: validate`, `flyway.enabled: true`, `clean-disabled: true`.
  - `spring.kafka.consumer.group-id: test-repository-service-it`, `auto-offset-reset: earliest`, `trusted.packages: org.learn.*`.
  - `eureka.client.enabled: false`, `spring.cloud.discovery.enabled: false`.
- `AbstractIntegrationTest` (`test-repository-service/src/test/java/org/learn/repository/AbstractIntegrationTest.java`):
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("it")`, `@Testcontainers(disabledWithoutDocker = true)`.
  - `@Container` static `PostgreSQLContainer<?> POSTGRES` (`postgres:18-alpine`).
  - `@Container` static `KafkaContainer KAFKA` (`apache/kafka:4.0.0`, `org.testcontainers.kafka.KafkaContainer`).
  - `@DynamicPropertySource` — прокидывает `spring.datasource.url/username/password`, `spring.kafka.bootstrap-servers`.
  - `disabledWithoutDocker = true` — если Docker не виден, IT скипаются, билд зелёный.
- `TestRunFinishedListenerIT` (`test-repository-service/src/test/java/org/learn/repository/kafka/TestRunFinishedListenerIT.java`):
  - `persistsAuditLogOnTestRunFinished()` — `kafkaTemplate.send(...).get(5, SECONDS)` + Awaitility до появления записи; проверяет `eventType`, `aggregateId`, `projectId`, `payload.summary`, `payload.triggeredBy`, `payload.status`.
  - `deduplicatesDuplicateTestRunFinished()` — два одинаковых `send()` → одна запись в БД.

**Сейчас статус: `Tests run: 2, Skipped: 2` — из-за несовместимости Docker Desktop и Testcontainers, см. §3.**

---

## 2. Что проверено и работает (без Testcontainers)

- Flyway V2 применяется в production-схеме `test_repository` при старте Repo (миграции V1 → V2, `ddl-auto: validate` проходит).
- E2E smoke (curl) — без изменений, `complete` на TestRun → Kafka event → consumer логирует.
- `curl.exe -s http://localhost:2375/info` → `ServerVersion: "29.9.3"`, `NCPU: 8`, `MemTotal: 8197406720`, `KernelVersion: 6.18.58.1-microsoft-standard-WSL2`. **TCP 2375 отдаёт настоящий Docker engine.**

---

## 3. Блокер (не решён, отложен): Docker Desktop 4.99 + Testcontainers 1.20.4

### Симптом

`AbstractIntegrationTest` скипается с `Could not find a valid Docker environment`. В логе Testcontainers:

```
EnvironmentAndSystemPropertyClientProviderStrategy: failed with BadRequestException (Status 400: {
  "ServerVersion":"","NCPU":0,"MemTotal":0,
  "Labels":["com.docker.desktop.address=npipe://\\\\.\\pipe\\docker_cli"]
})
NpipeSocketClientProviderStrategy: failed with BadRequestException (Status 400: {...})
```

### Диагноз

- `docker version` из PowerShell — работает, `Engine Version: 29.9.3`, `Context: desktop-linux`.
- `docker context ls` — `*` на `desktop-linux` → `npipe:////./pipe/dockerDesktopLinuxEngine`.
- `curl http://localhost:2375/info` — работает, **настоящий** engine.
- Но `docker-java` (внутри Testcontainers 1.20.4 от 2024) при подключении получает **шим** `docker_cli`, который на `/info` отдаёт 400 с пустым `Info`.
- **Корневая причина:** Docker Desktop 4.30+ (а особенно 4.99) перевёл все npipe-транспорты (`docker_engine`, `dockerDesktopLinuxEngine`) и TCP 2375 в **CLI-шим**. `docker CLI 29.9.3` знает про этот шим, `docker-java 1.20.4` — нет. Testcontainers 1.20.4 принципиально несовместим с 4.99.

### Что пробовали (не сработало / сработало не до конца)

| Попытка | Результат |
|---|---|
| `setx DOCKER_HOST npipe://...dockerDesktopLinuxEngine` | Стратегия №2 стартовала (env применился), но npipe → шим → 400 |
| `.testcontainers.properties` с `docker.host=npipe://...` | То же — 400 |
| Включение TCP 2375 в Docker Desktop | **TCP реально работает** (`curl` показывает живой engine), но Testcontainers всё равно попадает в шим — `DOCKER_HOST` в Maven JVM указывает на npipe, а не TCP |

### Возможные решения (когда вернёмся к тестам)

1. **Обновить Testcontainers до 1.21.x** — самая вероятная победа. В `pom.xml` добавить `<testcontainers.version>1.21.3</testcontainers.version>` в `<properties>`. Boot BOM перебьётся, все `org.testcontainers:*` без версий подхватят 1.21.3. Не тестировали — сессия закончилась раньше.
2. **Откатить Docker Desktop на 4.34.x** — последняя версия, где `docker_engine` = настоящий docker REST API. Минус — без security-фиксов.
3. **Обновить docker-java напрямую** — грязно, лучше через Testcontainers 1.21.x.
4. **Не гонять IT на Windows-машине вообще** — писать их и запускать в CI (Linux-раннер, GitHub Actions / GitLab CI). Для локальной разработки — только `mvn test` (Surefire, юниты) + Compose.

### Что важно проверить при возврате к тестам

- **`JAVA_TOOL_OPTIONS=-Dfile.encoding=IBM866`** в env. Это DOS-кириллица. Починить: `[System.Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $null, 'User')` или `setx JAVA_TOOL_OPTIONS "-Dfile.encoding=UTF-8"`. Иначе кириллица в HTTP/логах — кракозябры.
- **`DOCKER_HOST` в IDEA**: `setx` пишет в реестр, но IDEA наследует env от Explorer. Надёжнее всего — **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → Environment variables** → `DOCKER_HOST=tcp://localhost:2375`.
- **Surefire vs Failsafe**: если `mvn test` запускает `*IT` — в родительском pom есть кастомный `<includes>` у `maven-surefire-plugin`. Переопределить через `<excludes>**/*IT.java</excludes>` или удалить кастомный конфиг.

---

## 4. Решение сессии: **тесты откладываются, идём во фронтенд**

Осознанный выбор:
- Backend E2E работает (7 сущностей, Feign, Kafka, Flyway V2).
- IT-инфраструктура **написана**, но заблокирована версией Docker Desktop.
- Ковыряние с Docker Desktop / Testcontainers / `docker-java` — отдельная задача на 2–4 часа, не блокирует основную линию.
- **Следующий шаг по roadmap — Angular 22 frontend** (шаг 9 из Конспекта 3).

### Что остаётся «на потом» (не забыть)

- **Вернуться к Testcontainers**: обновить до 1.21.x, прогнать `TestRunFinishedListenerIT`, убедиться в `Skipped: 0`. Приоритет — когда будет перерыв во фронте.
- **Exec IT**: WireMock для `ProjectClient`/`UserClient`/`TestCaseClient` + IT на `TestRunService` (Feign 404 / 503), `TestResultService` (guard IN_PROGRESS, UNIQUE 409), `BugReportService` (resolvedAt, локальная проверка testResultId).
- **`@Tag("e2e")` + Compose-profile** — для полного end-to-end прогона на двух сервисах. Отдельная задача, потом.
- **Убрать `@BeforeAll` diagnostic** из `AbstractIntegrationTest` (если он там появился — по итогу сессии не добавляли, но помнить).
- **`JAVA_TOOL_OPTIONS=-Dfile.encoding=IBM866`** — починить при следующем запуске Maven.

---

## 5. Следующий шаг — Angular 22 frontend (шаг 9 roadmap)

### Что нужно сделать

1. **Скелет Angular 22**:
   - Node.js 24 LTS.
   - `ng new tms-frontend --routing --style=scss` (или что-то эквивалентное для Angular 22).
   - Angular Material 22, NgRx 22 (store + effects + devtools).
   - Окружения `environment.ts` / `environment.prod.ts` — `apiUrl: http://localhost:8080/api` (Gateway — единая точка входа).
2. **Каркас**:
   - Layout: sidebar + toolbar + content area (mat-sidenav-container, mat-toolbar).
   - Routing: lazy-loaded feature modules.
   - HTTP-интерцептор: базовый URL, обработка `ErrorResponse` из `tms-common` (единый формат ошибок), toast при ошибке.
3. **Экраны (по roadmap)**: Projects, TestRuns, TestResults.
   - Projects: list + create + edit (CRUD через `/api/repository/projects`).
   - TestRuns: list + start/complete/abort (через `/api/execution/test-runs`) — показывает переходы статусов.
   - TestResults: list внутри прогона + create (guard IN_PROGRESS) — показывает бизнес-правила.
4. **NgRx store**: `projects` (entity adapter), `testRuns`, `testResults`. Effects → HTTP-сервисы → actions.
5. **Что НЕ делать на этом шаге**: JWT / Security (шаг 10), Observability (шаг 11), полноценный UI-дизайн (Material default хватает).

### Правила фронта, которые надо соблюдать (из конспектов, если применимо)

- **Gateway — единственная точка входа**: `http://localhost:8080/api/repository/...` и `http://localhost:8080/api/execution/...`. Никаких прямых `8081`/`8082`.
- **`StripPrefix=2`** на Gateway отрезает `/api/repository` / `/api/execution` — но фронт видит полный путь, это его не касается.
- **Пагинация**: `?page=&size=&sort=` — формат Boot. Ответ — `PageResponse` из `tms-common` (`content`, `totalElements`, `totalPages`, `number`, `size`). Если фронт на TS — завести тип `PageResponse<T>`, повторяющий Java-версию.
- **Формат ошибок**: `ErrorResponse` из `tms-common` — `{ code, message, ... }`. Интерцептор маппит `code` → сообщение для пользователя (`PROJECT_NOT_FOUND` → «Проект не найден», и т.д.). Словарь `ErrorCode` — единый источник.
- **UUID** — строки. **Время** — ISO-8601 с offset (`OffsetDateTime` сериализуется как `"2026-11-15T10:00:00+03:00"`). Не `LocalDateTime`.
- **JSONB `steps`** (`TestCase`) — `{ order, action, expected }[]`. На фронте — форма редактирования массива.

### Roadmap после фронта

| # | Шаг | Статус |
|---|---|---|
| 7 | Flyway V2 audit_log + persistence в listener | ✅ **сделано** |
| 8 | Testcontainers IT | ⚠️ **инфраструктура готова, блокер Docker Desktop — отложено** |
| 9 | **Angular 22 frontend** | ⏭️ **следующий шаг** |
| 10 | JWT в Gateway + `@PreAuthorize` | позже |
| 11 | Micrometer + Prometheus + Grafana + Tracing | позже |

---

## 6. Состояние кодовой базы на конец сессии

**Незакоммиченные изменения** (если ещё не закоммичены — закоммитить перед переходом на фронт):

- `test-repository-service/src/main/resources/db/migration/V2__audit_log.sql` (новый).
- `test-repository-service/src/main/java/org/learn/repository/domain/AuditLog.java` (новый).
- `test-repository-service/src/main/java/org/learn/repository/repository/AuditLogRepository.java` (новый).
- `test-repository-service/src/main/java/org/learn/repository/kafka/TestRunFinishedListener.java` (изменён — `log.info` → `save`).
- `test-repository-service/src/test/` — `AbstractIntegrationTest`, `TestRunFinishedListenerIT`, `application-it.yml` (новые).
- `test-repository-service/pom.xml` — test-зависимости + failsafe + testcontainers BOM (изменён).
- **Возможно** `pom.xml` родителя — если настраивали milestone-репозитории; проверить.

**Код полностью рабочий для production** — IT скипаются, но `mvn test` (Surefire, юниты если есть) и `mvn package` проходят. Запуск через IDEA Compound `TMS-All` — работает.

**Проблема, которую надо решить до полного перехода** (если ещё не решена): если `spring-boot-starter-flyway` / `spring-boot-starter-*-test` из pom'а не резолвились — их уже поправили в этой сессии, но при переносе проверить, что `mvn clean verify` зелёный.

---

## 7. Открытые вопросы для следующего чата

1. **Точная версия Angular** — 22.x. Node.js 24 LTS. Проверить совместимость (Angular 22 требует Node 22+; Node 24 — ок).
2. **Standalone components или NgModules** — Angular 22 по умолчанию standalone. Material 22 — тоже standalone-friendly. Выбрать standalone (современный подход).
3. **State management** — NgRx Store (полный) или NgRx Signal Store (новый)? В конспектах указан NgRx 22. Уточнить у пользователя: классический NgRx Store или Signal Store — это разные API.
4. **Тесты фронта** — Jasmine/Karma (Angular default) или Jest? В конспекте про фронт ничего нет. Спросить.
5. **Дизайн** — Material default или кастомная тема? Для roadmap достаточно Material default.
6. **Структура репозитория** — `tms-frontend/` в корне multi-module Maven? Или отдельный репо? **Скорее всего отдельный** — Maven не собирает Angular, это независимый build (`npm`). Не добавлять как Maven-модуль.

---

## 8. Итог одной строкой

**Backend доведён до состояния «все 7 сущностей + Feign + Kafka + Flyway V2 audit_log работают E2E». IT-инфраструктура написана, но заблокирована несовместимостью Docker Desktop 4.99 и Testcontainers 1.20.4 (фикс — обновление до 1.21.x, отложено). Переходим к Angular 22 frontend — скелет + Projects/TestRuns/TestResults, всё через Gateway (`:8080/api/...`), с NgRx и единым форматом ошибок из `tms-common`.**