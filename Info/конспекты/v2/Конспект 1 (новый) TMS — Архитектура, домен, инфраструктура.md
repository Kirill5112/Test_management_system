# Конспект 1 (новый): TMS — Архитектура, домен, инфраструктура

> Объединённая версия Конспектов 1, 2, 3. Убраны повторы (промежуточные конфиги, ранние версии справочников), сохранены все технические детали. Финальные правила проекта и словарь ошибок — в Документе 2. Реализация endpoints/Feign/Kafka — в Документе 3.

---

## 1. ТЗ (исходные требования)

**Стек и инфраструктура:**
- **Frontend:** Angular
- **Backend:** Java + Spring Boot, минимум 2 stateless-микросервиса
- **Взаимодействие:** Kafka (async) + REST/OpenFeign (sync)
- **Инфраструктура:** Spring Cloud Gateway (API Gateway) + Eureka (Service Discovery)
- **БД:** PostgreSQL (реляционная)

**Предметная область:** TMS — система управления тестированием (7 сущностей).

---

## 2. Зафиксированный стек (не менять)

| Слой | Компонент | Версия |
|---|---|---|
| Backend | Java | 21 LTS |
| Backend | Spring Boot | 4.0.x (milestone) |
| Backend | Spring Cloud | 2025.1.x (milestone) |
| Infra | Apache Kafka | 4.0.x (KRaft, без ZooKeeper) |
| DB | PostgreSQL | 18.x |
| DB | Flyway | 11.x (managed by Boot) + **`flyway-database-postgresql` явно в pom** |
| Frontend | Angular | 22.x |
| Frontend | Node.js | 24 LTS |
| Frontend | Angular Material / NgRx | 22.x |
| Build | Maven | bundled 3.9.x в IDEA |
| IDE | IntelliJ IDEA | 2025.2+ (Ultimate) |

**Отвергнутые альтернативы:** Spring Boot 3.5.x + Angular 21.x (консервативный вариант) — не выбраны.

**Правило:** версии Kafka, JUnit, Mockito, PostgreSQL JDBC, Flyway **не** указываются вручную — идут из Boot BOM. Spring Cloud — только в `dependencyManagement` родителя.

**Порты (фиксированы):**
| Порт | Компонент |
|---|---|
| 8761 | Eureka |
| 8080 | API Gateway |
| 8081 | Test Repository Service |
| 8082 | Test Execution Service |
| 5432 | PostgreSQL |
| 9092 | Kafka (host) |
| 8090 | Kafka UI |

---

## 3. Архитектурное разделение

| Компонент | Назначение |
|---|---|
| **API Gateway** | Единая точка входа для Angular |
| **Eureka** | Регистрация и обнаружение сервисов |
| **Test Repository Service** | User, Project, ProjectMember, TestSuite, TestCase |
| **Test Execution Service** | TestRun, TestResult, BugReport |
| **Kafka** | Async-события (`TestRunFinished` и др.) |

**Принципы:**
- Оба микросервиса — **stateless**, масштабируются горизонтально.
- Cross-service связи — **только по UUID, без FK** между сервисами. Валидация ссылок — на уровне приложения (Feign).
- FK — только **внутри своей схемы**.
- При одном PostgreSQL — отдельные схемы: `test_repository`, `test_execution`.
- Наружу отдаём **DTO**, не entity.
- Время в entity — `OffsetDateTime` (под `TIMESTAMPTZ`), **не** `LocalDateTime`.
- `ddl-auto: validate` всегда; схему меняет **только** Flyway.

---

## 4. Доменная модель (7 сущностей)

### 4.1. Test Repository Service

#### User (`users`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| username | VARCHAR(50) | UNIQUE, NOT NULL |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| first_name | VARCHAR(100) | |
| last_name | VARCHAR(100) | |
| role | VARCHAR(30) | NOT NULL (`ADMIN`/`MANAGER`/`TESTER`/`DEVELOPER`) |
| active | BOOLEAN | NOT NULL, default true |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

#### Project (`projects`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| key | VARCHAR(20) | UNIQUE, NOT NULL |
| name | VARCHAR(255) | NOT NULL |
| description | TEXT | |
| owner_id | UUID | NOT NULL → users.id |
| status | VARCHAR(30) | NOT NULL (`ACTIVE`/`ARCHIVED`) |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

#### ProjectMember (`project_members`) — M:N User↔Project
| Поле | Тип | Ограничения |
|---|---|---|
| project_id | UUID | FK → projects.id |
| user_id | UUID | FK → users.id |
| role_in_project | VARCHAR(30) | `MANAGER`/`TESTER`/`DEVELOPER` |
| **PK** | (project_id, user_id) | |

#### TestSuite (`test_suites`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL, FK → projects.id |
| parent_suite_id | UUID | NULL, FK → test_suites.id (иерархия) |
| name | VARCHAR(255) | NOT NULL |
| description | TEXT | |
| position | INT | |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

Индексы: `(project_id, parent_suite_id)`, `(project_id, name)`

#### TestCase (`test_cases`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| suite_id | UUID | NOT NULL, FK → test_suites.id |
| title | VARCHAR(255) | NOT NULL |
| description | TEXT | |
| preconditions | TEXT | |
| steps | JSONB | Список шагов (order/action/expected) |
| expected_result | TEXT | |
| priority | VARCHAR(20) | NOT NULL (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) |
| type | VARCHAR(20) | NOT NULL (`MANUAL`/`AUTOMATED`) |
| status | VARCHAR(20) | NOT NULL (`DRAFT`/`ACTIVE`/`DEPRECATED`) |
| created_by_id | UUID | → users.id |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

Пример `steps`:
```json
[{"order":1,"action":"Открыть логин","expected":"Страница загружена"}]
```

### 4.2. Test Execution Service

#### TestRun (`test_runs`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| project_id | UUID | NOT NULL (ref) |
| name | VARCHAR(255) | NOT NULL |
| description | TEXT | |
| status | VARCHAR(30) | NOT NULL (`CREATED`/`IN_PROGRESS`/`COMPLETED`/`ABORTED`) |
| environment | VARCHAR(100) | |
| build_version | VARCHAR(100) | |
| started_at / finished_at | TIMESTAMPTZ | |
| created_by_id | UUID | NOT NULL (ref) |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

#### TestResult (`test_results`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| test_run_id | UUID | NOT NULL, FK → test_runs.id |
| test_case_id | UUID | NOT NULL (ref) |
| status | VARCHAR(20) | NOT NULL (`PASSED`/`FAILED`/`BLOCKED`/`SKIPPED`) |
| comment | TEXT | |
| actual_result | TEXT | |
| duration_ms | BIGINT | |
| executed_by_id | UUID | (ref) |
| executed_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

**UNIQUE (test_run_id, test_case_id)**

#### BugReport (`bug_reports`)
| Поле | Тип | Ограничения |
|---|---|---|
| id | UUID | PK |
| test_result_id | UUID | NULL, FK → test_results.id |
| project_id | UUID | NOT NULL (ref) |
| title | VARCHAR(255) | NOT NULL |
| description | TEXT | |
| status | VARCHAR(30) | NOT NULL (`OPEN`/`IN_PROGRESS`/`RESOLVED`/`CLOSED`/`REJECTED`) |
| severity | VARCHAR(20) | NOT NULL (`BLOCKER`/`CRITICAL`/`MAJOR`/`MINOR`/`TRIVIAL`) |
| priority | VARCHAR(20) | NOT NULL (`HIGH`/`MEDIUM`/`LOW`) |
| assignee_id | UUID | (ref) |
| reporter_id | UUID | NOT NULL (ref) |
| resolved_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

---

## 5. Карта связей

```
User 1 ── N Project (owner_id)
User M ── N Project (через ProjectMember)

Project 1 ── N TestSuite
TestSuite 0..1 ── N TestSuite (parent/children)
TestSuite 1 ── N TestCase

TestRun 1 ── N TestResult
TestResult 1 ── 0..1 BugReport

Cross-service (UUID-ссылки, без FK):
  TestRun.projectId     → Project
  TestRun.createdById   → User
  TestResult.testCaseId → TestCase
  TestResult.executedById → User
  BugReport.projectId   → Project
  BugReport.reporterId  → User
  BugReport.assigneeId  → User
```

---

## 6. Kafka-событие `TestRunFinished`

**Топик:** `test-run-events` (константа `KafkaTopics.TEST_RUN_EVENTS` в `tms-common`)
**Key:** `testRunId` (String)
**Value:** `TestRunFinishedEvent` (record в `tms-common`)

```json
{
  "eventType": "TestRunFinished",
  "testRunId": "...",
  "projectId": "...",
  "status": "COMPLETED",
  "finishedAt": "...",
  "summary": { "total": 10, "passed": 7, "failed": 2, "blocked": 1 },
  "triggeredBy": "..."
}
```

**Назначение:** уведомления, отчёты, аудит. `total` в `summary` **включает** `SKIPPED`.

---

## 7. PlantUML — компактная доменная модель

```plantuml
@startuml TMS_Domain_Compact
!theme plain
skinparam classAttributeIconSize 0
skinparam linetype ortho
skinparam nodesep 40
skinparam ranksep 50

skinparam class {
    BackgroundColor<<Repository>> #E3F2FD
    BorderColor<<Repository>> #1565C0
    BackgroundColor<<Execution>> #FFF3E0
    BorderColor<<Execution>> #EF6C00
}

class User <<Repository>> { id : UUID \n username \n email \n role }
class Project <<Repository>> { id : UUID \n key \n name \n ownerId : UUID }
class ProjectMember <<Repository>> { projectId : UUID \n userId : UUID \n roleInProject }
class TestSuite <<Repository>> { id : UUID \n projectId : UUID \n parentSuiteId : UUID \n name }
class TestCase <<Repository>> { id : UUID \n suiteId : UUID \n title \n priority \n status }

class TestRun <<Execution>> { id : UUID \n projectId : UUID \n name \n status \n createdById : UUID }
class TestResult <<Execution>> { id : UUID \n testRunId : UUID \n testCaseId : UUID \n status }
class BugReport <<Execution>> { id : UUID \n testResultId : UUID \n title \n status \n severity }

User "1" -- "0..*" Project : owns
User "1" -- "0..*" ProjectMember
Project "1" -- "0..*" ProjectMember

Project "1" -- "0..*" TestSuite : contains
TestSuite "0..1" -- "0..*" TestSuite : parent
TestSuite "1" -- "0..*" TestCase : contains

TestRun "1" *-- "1..*" TestResult : includes
TestResult "1" -- "0..1" BugReport : produces

TestRun ..> Project
TestResult ..> TestCase
BugReport ..> Project
@enduml
```

---

## 8. Multi-module Maven — структура проекта

```
tms/
├── pom.xml                          (parent, packaging=pom)
├── tms-common/                      (общие DTO, enum'ы, исключения, Kafka-события)
├── eureka-server/                   (port 8761)
├── api-gateway/                     (port 8080, WebFlux-only)
├── test-repository-service/         (port 8081, схема test_repository)
└── test-execution-service/          (port 8082, схема test_execution)
```

**Координаты:**
- GroupId родителя: `com.example.tms`
- ArtifactId корня: `tms`
- Version: `0.0.1-SNAPSHOT`
- **Базовый пакет — `org.learn`** (отличается от groupId-родителя, не путать!)

**Роли модулей:**

| Модуль | Роль | Dependencies (Initializr) |
|---|---|---|
| `eureka-server` | Service Discovery | Eureka Server |
| `api-gateway` | Единая точка входа, WebFlux | Gateway, Eureka Client, Actuator |
| `test-repository-service` | User, Project, ProjectMember, TestSuite, TestCase | Web, JPA, PostgreSQL, Flyway, Eureka Client, OpenFeign, Kafka, Actuator, Validation, Config Processor |
| `test-execution-service` | TestRun, TestResult, BugReport | то же, что repository |
| `tms-common` | Общие DTO/enum'ы/исключения | `jakarta.servlet-api`, `spring-tx` (**scope `provided`**) |

**Явно НЕ добавляли на старте:** Security (`-starter-security`), OAuth2, Resilience4j, Micrometer Tracing, Config Server, Spring Cloud Bus. Только `spring-security-crypto` — точечно в Repo.

---

## 9. Создание проектов в IDEA

**Способ:** `New Project → Maven` (plain, **не** Maven Archetype) для корня + `New Module → Spring Boot` (встроенный Initializr) для модулей.

**Maven Archetype не используем** — шаблоны устарели, для Boot не рекомендован Spring-командой.

**Ключевая ручная правка:** в каждом дочернем `pom.xml` заменить `<parent>` с `spring-boot-starter-parent` на `com.example.tms:tms`. Убрать `<groupId>`/`<version>` из дочерних (наследуются).

**Настройки IDEA:**
- Maven home: Bundled, JDK for importer: 21.
- Java Compiler target: 21.
- Encoding: UTF-8.
- `Import Maven projects automatically` — включено.
- Annotation Processing — включён (Lombok).
- Плагины: Lombok, MapStruct Support, Docker, SonarLint (опц.).
- Полезные окна: Endpoints, Services, Database, HTTP Client.

**Run configurations:** 4 Spring Boot + **Compound «TMS-All»** в порядке **Eureka → Gateway → Repo → Exec**. VM options для бизнес-сервисов: `-Dspring.profiles.active=local`. Между стартами в Compound — **Wait N seconds** (10–15 сек), иначе гонка Eureka.

---

## 10. Родительский POM (эталон)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.x</version>
    <relativePath/>
</parent>

<groupId>com.example.tms</groupId>
<artifactId>tms</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
    <java.version>21</java.version>
    <spring-cloud.version>2025.1.x</spring-cloud.version>
</properties>

<modules>
    <module>tms-common</module>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>test-repository-service</module>
    <module>test-execution-service</module>
</modules>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 11. Обязательные зависимости в pom бизнес-сервисов

```xml
<!-- базовые -->
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-actuator

<!-- БД -->
org.postgresql:postgresql  (scope=runtime)
flyway-core
flyway-database-postgresql            ← КРИТИЧНО, без него Unsupported Database

<!-- service discovery / коммуникация -->
spring-cloud-starter-netflix-eureka-client   ← НЕ -server
spring-cloud-starter-openfeign
spring-cloud-starter-loadbalancer            ← обычно транзитивно с openfeign
spring-kafka

<!-- crypto (только Repo) -->
spring-security-crypto                       ← НЕ -starter-security
```

**Gateway:** `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-actuator`. **Без** `starter-web`, **без** JPA.

Проверка:
```bash
mvn -q dependency:tree -pl test-repository-service | grep -E 'eureka|flyway|postgresql|kafka|jpa'
```

---

## 12. Конфигурация сервисов (рабочие `application.yml`)

### 12.1. Eureka (`eureka-server`, 8761)

```yaml
server:
  port: 8761
spring:
  application:
    name: eureka-server
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: false
logging:
  level:
    com.netflix.eureka: WARN
    com.netflix.discovery: WARN
```
Класс — с `@EnableEurekaServer`.

### 12.2. Gateway (`api-gateway`, 8080)

```yaml
server:
  port: 8080
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false                    # маршруты только явные
      routes:
        - id: test-repository-service
          uri: lb://test-repository-service
          predicates:
            - Path=/api/repository/**
          filters:
            - StripPrefix=2
        - id: test-execution-service
          uri: lb://test-execution-service
          predicates:
            - Path=/api/execution/**
          filters:
            - StripPrefix=2
eureka:
  client:
    enabled: true
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,gateway
```

**⚠ Gateway — WebFlux-only.** `spring-boot-starter-web` на classpath ломает gateway-фильтры и запускает servlet-стек. Проверять `mvn dependency:tree`.

### 12.3. Бизнес-сервисы (8081 / 8082)

Отличия — **только** `name`, `port`, `hikari.schema`, `default_schema`, `flyway.schemas`/`default-schema`, `kafka.consumer.group-id`. Остальное идентично.

```yaml
server:
  port: 8081                                # 8082 для exec

spring:
  application:
    name: test-repository-service           # test-execution-service для exec

  datasource:
    url: jdbc:postgresql://localhost:5432/tms
    username: tms
    password: tms
    driver-class-name: org.postgresql.Driver
    hikari:
      schema: test_repository               # test_execution для exec
      maximum-pool-size: 10

  jpa:
    hibernate:
      ddl-auto: validate                    # ← схему создаёт Flyway
      default_schema: test_repository       # test_execution для exec
    properties:
      hibernate:
        format_sql: true
    open-in-view: false

  flyway:
    enabled: true
    schemas: test_repository                # test_execution для exec
    default-schema: test_repository         # test_execution для exec
    baseline-on-migrate: false

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: test-repository-service     # test-execution-service для exec
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "org.learn.*"   # ← ИСПРАВЛЕНО (было com.example.tms.*)

eureka:
  client:
    enabled: true
    service-url:
      defaultZone: http://localhost:8761/eureka/   # ← camelCase обязателен!
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    org.flywaydb: INFO
    org.hibernate.SQL: INFO
```

**⚠ Три места согласования схемы** (должны совпадать, иначе `ddl-auto: validate` падает с «missing table»):
- `spring.datasource.hikari.schema`
- `spring.jpa.hibernate.default_schema`
- `spring.flyway.schemas` + `spring.flyway.default-schema`

---

## 13. Docker Compose — локальная инфраструктура

### 13.1. Что должно быть

1. **PostgreSQL 18**
   - БД: `tms`, user: `tms`, password: `tms`.
   - `init.sql` создаёт схемы `test_repository` и `test_execution` (применяется один раз при первом старте контейнера).
   - Healthcheck: `pg_isready`.
   - Volume для персистентности.
   - **Postgres 18: volume в `/var/lib/postgresql`** (не `.../data`) — в 18 PGDATA сместился на `/var/lib/postgresql/18/docker`.

2. **Apache Kafka 4.0 в режиме KRaft**
   - Один broker для локала.
   - Без ZooKeeper.
   - Healthcheck на готовность broker.
   - Volume для логов.

3. **Kafka UI** (`provectuslabs/kafka-ui`)
   - Порт 8090 (не 8080 — занят Gateway).
   - Подключён к нашему broker.

4. **Зависимости запуска:** бизнес-сервисы должны ждать healthy Postgres и Kafka (`depends_on: condition: service_healthy` — если поднимем сервисы в compose).

### 13.2. Метрики успеха

- `docker compose up -d` → всё зелёное.
- `psql localhost:5432/tms` → видны обе схемы.
- `localhost:8090` (Kafka UI) → broker онлайн, топиков пока нет (нормально до первого продюсера).
- Запустить все 4 сервиса через Compound в IDEA → в Eureka 3 приложения (кроме самой Eureka), Gateway отвечает на `/actuator/health`.

---

## 14. Flyway V1 — миграции схем

### 14.1. Структура

```
test-repository-service/src/main/resources/db/migration/V1__init_schema.sql
test-execution-service/src/main/resources/db/migration/V1__init_schema.sql
```

Порядок таблиц внутри каждого файла — строго по зависимостям FK:
- **Repo:** `users → projects → project_members → test_suites → test_cases`
- **Exec:** `test_runs → test_results → bug_reports`

### 14.2. Ключевые решения

| Решение | Обоснование |
|---|---|
| **FK только внутри своей схемы** | `projects.owner_id → users`, `test_suites.project_id → projects`, `test_results.test_run_id → test_runs` — локальные. Всё межсервисное — **только UUID, без FK** |
| **Cross-service UUID без FK** | `test_runs.project_id`, `test_results.test_case_id`, `bug_reports.project_id`, все `*_by_id` — это ref-поля, не FK. Валидация — Feign |
| **`ON DELETE CASCADE`** для дочерних внутри схемы | Удаление Project чистит suites/cases/members; удаление TestRun чистит results |
| **`ON DELETE SET NULL`** для `bug_reports.test_result_id` | Баг живёт дольше результата |
| **`CHECK` по всем enum** (`role`, `status`, `priority`, `severity`, `type`) | Защита от опечаток на уровне БД, значения — строго по §4 |
| **`UNIQUE (test_run_id, test_case_id)`** в `test_results` | Один результат на тест-кейс в прогоне |
| **`DEFAULT NOW()`** на `created_at`/`updated_at` | Прямые SQL-инсерты тоже работают; Hibernate всё равно проставит `@CreationTimestamp` |
| **Индексы на все FK + cross-service ref-поля** | Postgres не создаёт их автоматически; критично для `WHERE project_id = ?` |
| **`TIMESTAMPTZ`** везде | В entity — `OffsetDateTime`. `LocalDateTime` — нет |
| **Никаких `DROP IF EXISTS` в V1** | Flyway forward-only; V1 должна быть идемпотентна на чистой БД |
| **`steps JSONB`** в `test_cases` | Список шагов `[{order, action, expected}]` — гибко, без отдельной таблицы |

### 14.3. Структура таблиц — сводно

**test_repository:**
- `users`: PK id, UNIQUE username/email, `role` CHECK(ADMIN/MANAGER/TESTER/DEVELOPER), `active` default true
- `projects`: FK owner_id→users, UNIQUE key, `status` CHECK(ACTIVE/ARCHIVED)
- `project_members`: PK(project_id, user_id), FK на обе стороны, `role_in_project` CHECK
- `test_suites`: FK project_id, self-FK parent_suite_id (иерархия), индексы `(project_id, parent_suite_id)` и `(project_id, name)`
- `test_cases`: FK suite_id, FK created_by_id, `steps JSONB`, `priority/type/status` CHECK

**test_execution:**
- `test_runs`: `project_id` и `created_by_id` — **UUID ref, без FK**. `status` CHECK(CREATED/IN_PROGRESS/COMPLETED/ABORTED)
- `test_results`: FK `test_run_id → test_runs` (CASCADE), `test_case_id` и `executed_by_id` — UUID ref. UNIQUE(test_run_id, test_case_id). `status` CHECK(PASSED/FAILED/BLOCKED/SKIPPED)
- `bug_reports`: FK `test_result_id → test_results` (SET NULL). `project_id`/`assignee_id`/`reporter_id` — UUID ref. `status/severity/priority` CHECK

---

## 15. Диагностированные проблемы и фиксы (не наступить снова)

### 15.1. Maven не видит дочерние модули

**Чек-лист диагностики:**
1. `<modules>` в родителе — точные имена папок, без слэшей.
2. `<parent>` в дочернем — точное совпадение `groupId:artifactId:version`.
3. `<packaging>pom</packaging>` у корня.
4. `Maven → Reload All Maven Projects`.
5. `File → Invalidate Caches and Restart…` + открыть корневой `pom.xml`.
6. **Не** добавлять дочерние через «Add as Maven Project».
7. Проверка: `Show Effective POM` для дочернего — видны свойства родителя.
8. Контрольная сборка `mvn clean install` из корня — собирает все модули подряд.

**Статус:** ✅ решено, все модули распознаются.

### 15.2. `Failed to start bean 'eurekaAutoServiceRegistration'` → NPE `getEurekaClient()` is null

**Причина (корневая):** в `application.yml` не было ни `spring.application.name`, ни `eureka.client.service-url.defaultZone`. Клиент создавался, но регистрация уходила в никуда — в Boot 4.0.x / Cloud 2025.1.x (milestone!) lazy-прокси при недоступном сервере резолвится в `null` вместо явной ошибки.

**Три обязательных элемента для регистрации:**
1. `spring.application.name`
2. `eureka.client.service-url.defaultZone: http://localhost:8761/eureka/` — **camelCase**, не kebab-case, с trailing `/`
3. `spring-cloud-starter-netflix-eureka-client` на classpath (**не** `-server`)

**Косвенное следствие:** пока не чинится Eureka, **Flyway не запускается** (сервис падает до миграций). Быстрый обход — `eureka.client.enabled: false` + `spring.cloud.discovery.enabled: false` в `application-local.yml`. Потом вернуть.

**Если повторится:** добавить `eureka.client.fail-fast: true` — вместо NPE получим внятное «Cannot execute request on any known server».

### 15.3. Порядок запуска критичен

Eureka-сервер должен быть **UP** до старта бизнес-сервисов. В IDEA Compound по умолчанию стартует параллельно — гонка. Решения:
- Запускать вручную по одному.
- В Run Configuration → Compound → после выбора сервиса → Edit → **After launch → Wait N seconds** (10–15 сек между стартами).

### 15.4. `flyway-database-postgresql` — не забыть

С Flyway 10+ диалект PostgreSQL вынесен в отдельный артефакт. Без него — `Unsupported Database: PostgreSQL 18.0` при старте. Boot BOM версии подтягивает, но сам артефакт надо **явно** указать в `<dependencies>`.

### 15.5. Согласование схемы в трёх местах

Для корректной работы Postgres+Hibernate+Flyway **все три параметра** должны совпадать:
```yaml
spring.datasource.hikari.schema
spring.jpa.hibernate.default_schema
spring.flyway.default-schema      # + spring.flyway.schemas
```
Если разъедутся — Hibernate ищет таблицы не в той схеме, `ddl-auto: validate` падает с «missing table», хотя `\dt` показывает их на месте.

### 15.6. Lombok и MapStruct не работали в Maven

**Симптом:** `cannot find symbol` (нет геттеров/сеттеров) и `variable ... not initialized in default constructor`.

**Причина:** В `pom.xml` сервисов был явно объявлен `maven-compiler-plugin` с `<annotationProcessorPaths>`, где был только `spring-boot-configuration-processor`. Maven игнорирует процессоры из `classpath`, если этот блок задан.

**Решение:** В `maven-compiler-plugin` добавлены **все 4** процессора в строгом порядке:
1. `spring-boot-configuration-processor`
2. `lombok`
3. `lombok-mapstruct-binding` (**версия 0.2.0**)
4. `mapstruct-processor`

Блок `<executions>` убран (чтобы конфигурация применялась по умолчанию ко всем фазам).

### 15.7. Автоконфигурация `tms-common` не подхватывается

**Симптом:** `AutoConfiguration.imports` не работает, `GlobalExceptionHandler` не регистрируется, ошибки БД возвращают 500 вместо 409.

**Решение:** В главные классы `@SpringBootApplication` бизнес-сервисов добавлен явный `@Import(CommonAutoConfiguration.class)`. Это гарантированно регистрирует `GlobalExceptionHandler` в контексте.

---

## 16. Verification checklist

```bash
# 1) Eureka
curl -i http://localhost:8761/eureka/apps            # 200, XML с applications
curl -s http://localhost:8761/eureka/apps | grep -E '<name>|<status>'   # 3 UP

# 2) Gateway
curl -s http://localhost:8080/actuator/health        # UP
curl -s http://localhost:8080/api/repository/actuator/health    # UP (StripPrefix=2 → Repo)
curl -s http://localhost:8080/api/execution/actuator/health     # UP (→ Exec)

# 3) Flyway history
docker exec -it tms-postgres psql -U tms -d tms -c \
  "SELECT 'repo' AS s, version, success FROM test_repository.flyway_schema_history
   UNION ALL SELECT 'exec', version, success FROM test_execution.flyway_schema_history;"

# 4) Таблицы
docker exec -it tms-postgres psql -U tms -d tms -c "\dt test_repository.*"
docker exec -it tms-postgres psql -U tms -d tms -c "\dt test_execution.*"

# 5) Kafka (топиков ещё нет — нормально)
docker exec -it tms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## 17. Соглашения (сводка, полная версия — Документ 2)

- Оба бизнес-сервиса **stateless**, масштабируются горизонтально.
- Cross-service — **только UUID, без FK**. Валидация — Feign.
- Одна БД `tms`, **разные схемы** `test_repository` / `test_execution`.
- Наружу — **DTO**, не entity.
- Kafka-топик: `test-run-events`, **ключ сообщения — `testRunId`**.
- Событие: `TestRunFinished` (§6).
- Enum-значения — строго по §4, продублированы в `CHECK`-констрейнтах БД.
- Время в entity — `OffsetDateTime`, не `LocalDateTime`.
- `ddl-auto: validate` всегда; схему меняет только Flyway.
- `flyway-database-postgresql` — явно в pom.
- Postgres 18: volume в `/var/lib/postgresql` (не `.../data`).
- Порты фиксированы: 8761 / 8080 / 8081 / 8082 / 5432 / 9092 / 8090.
- Базовый пакет — `org.learn`.

---