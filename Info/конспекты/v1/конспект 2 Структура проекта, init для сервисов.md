# Конспект: настройка окружения TMS (итог)

## 1. Стек (зафиксирован)

**Основной вариант:**

| Слой | Компонент | Версия |
|---|---|---|
| Backend | Java | 21 LTS |
| Backend | Spring Boot | 4.0.x |
| Backend | Spring Cloud | 2025.1.x |
| Infra | Apache Kafka | 4.0.x (KRaft, без ZooKeeper) |
| DB | PostgreSQL | 18.x |
| DB | Flyway | 11.x (managed by Boot) |
| Frontend | Angular | 22.x |
| Frontend | Node.js | 24 LTS |
| Frontend | Angular Material / NgRx | 22.x |
| Build | Maven | bundled 3.9.x в IDEA |
| IDE | IntelliJ IDEA | 2025.2+ (Ultimate) |

**Отвергнутые альтернативы:** Spring Boot 3.5.x + Angular 21.x (консервативный вариант) — не выбраны.

**Правило:** версии Kafka, JUnit, Mockito, PostgreSQL JDBC, Flyway **не** указываются вручную — идут из Boot BOM. Spring Cloud — только в `dependencyManagement` родителя.

---

## 2. Архитектура проекта (multi-module Maven)

Один корневой проект `tms`, 4 модуля:

```
tms/
├── pom.xml                          (parent, packaging=pom)
├── eureka-server/                   (port 8761)
├── api-gateway/                     (port 8080)
├── test-repository-service/         (port 8081)
└── test-execution-service/          (port 8082)
```

**Координаты:**
- GroupId: `com.example.tms`
- ArtifactId корня: `tms`
- Version: `0.0.1-SNAPSHOT`
- Base package: `com.example.tms.<service>`

**Роли модулей:**

| Модуль | Роль | Dependencies (Initializr) |
|---|---|---|
| `eureka-server` | Service Discovery | Eureka Server |
| `api-gateway` | Единая точка входа, WebFlux | Gateway, Eureka Client, Actuator |
| `test-repository-service` | User, Project, ProjectMember, TestSuite, TestCase | Web, JPA, PostgreSQL, Flyway, Eureka Client, OpenFeign, Kafka, Actuator, Validation, Config Processor |
| `test-execution-service` | TestRun, TestResult, BugReport | то же, что repository |

**Явно НЕ добавляли на старте:** Security, OAuth2, Resilience4j, Micrometer Tracing, Config Server, Spring Cloud Bus, Spring Cloud Bus.

---

## 3. Как создавались проекты в IDEA

**Способ:** `New Project → Maven` (plain, **не** Maven Archetype) для корня + `New Module → Spring Boot` (встроенный Initializr) для модулей.

**Maven Archetype не используем** — шаблоны устарели, для Boot не рекомендован Spring-командой.

**Ключевая ручная правка:** в каждом дочернем `pom.xml` заменить `<parent>` с `spring-boot-starter-parent` на `com.example.tms:tms`. Убрать `<groupId>`/`<version>` из дочерних (наследуются).

---

## 4. Родительский POM (эталон)

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

## 5. Конфигурация сервисов (согласована)

### Eureka (`eureka-server`, 8761)
```yaml
eureka.client.register-with-eureka: false
eureka.client.fetch-registry: false
eureka.server.enable-self-preservation: false
```
Класс: `@EnableEurekaServer`.

### Gateway (`api-gateway`, 8080)
- Только WebFlux (**не** добавлять `spring-boot-starter-web`).
- `discovery.locator.enabled: false` — маршруты явные.
- Маршруты:
  - `/api/repository/**` → `lb://test-repository-service`, `StripPrefix=2`
  - `/api/execution/**` → `lb://test-execution-service`, `StripPrefix=2`

### Бизнес-сервисы (8081, 8082)
- Одна БД `tms`, **разные схемы**: `test_repository`, `test_execution`.
- `hikari.schema` + `hibernate.default_schema` + `flyway.schemas`/`default-schema`.
- `jpa.hibernate.ddl-auto: validate` — схему создаёт Flyway, не Hibernate.
- `jpa.open-in-view: false`.
- Kafka producer — `StringSerializer` + `JsonSerializer`.
- Kafka consumer — `StringDeserializer` + `JsonDeserializer`, `spring.json.trusted.packages: com.example.tms.*`.
- Eureka client — `prefer-ip-address: true`.
- Actuator — `health,info,metrics`.

---

## 6. Что настроено в IDEA

- **Run configurations:** 4 Spring Boot + **Compound «TMS-All»** в порядке Eureka → Gateway → Repo → Exec.
- **VM options** для бизнес-сервисов: `-Dspring.profiles.active=local`.
- **Settings:**
  - Maven home: Bundled, JDK for importer: 21.
  - Java Compiler target: 21.
  - Encoding: UTF-8.
  - `Import Maven projects automatically` — включено.
  - Annotation Processing — включён (Lombok).
- **Плагины:** Lombok, MapStruct Support, Docker, SonarLint (опц.).
- **Полезные окна:** Endpoints, Services, Database, HTTP Client.

---

## 7. Проблема с Maven — РЕШЕНА

**Симптом:** IDEA не видела дочерние модули как часть родительского.

**Чек-лист диагностики (на будущее):**
1. `<modules>` в родителе — точные имена папок, без слэшей.
2. `<parent>` в дочернем — точное совпадение `groupId:artifactId:version`.
3. `<packaging>pom</packaging>` у корня.
4. `Maven → Reload All Maven Projects`.
5. `File → Invalidate Caches and Restart…` + открыть корневой `pom.xml`.
6. **Не** добавлять дочерние через «Add as Maven Project».
7. Проверка: `Show Effective POM` для дочернего — видны свойства родителя.
8. Контрольная сборка `mvn clean install` из корня — собирает все модули подряд.

**Статус:** ✅ проблема устранена, все 4 модуля распознаются корректно.

---

## 8. Следующий шаг — Docker

**Цель:** поднять локальную инфраструктуру одной командой.

### Что должно быть в `docker-compose.yml`

1. **PostgreSQL 18**
   - БД: `tms`, user: `tms`, password: `tms`.
   - `init.sql` создаёт схемы `test_repository` и `test_execution`.
   - Healthcheck: `pg_isready`.
   - Volume для персистентности.

2. **Apache Kafka 4.0 в режиме KRaft**
   - Один broker для локали.
   - Без ZooKeeper.
   - Healthcheck на готовность broker.
   - Volume для логов.

3. **Kafka UI** (`provectuslabs/kafka-ui`)
   - Порт 8090 (не 8080 — занят Gateway).
   - Подключён к нашему broker.

4. **Зависимости запуска:** бизнес-сервисы должны ждать healthy Postgres и Kafka (`depends_on: condition: service_healthy` — если поднимем сервисы в compose позже).

### После Docker — вехи для проверки

- `docker compose up -d` → всё зелёное.
- `psql localhost:5432/tms` → видны обе схемы.
- `localhost:8090` (Kafka UI) → broker онлайн, топиков пока нет.
- Запустить все 4 сервиса через Compound в IDEA → в Eureka 3 приложения (кроме самого Eureka), Gateway отвечает на `/actuator/health`.
- Flyway при старте бизнес-сервисов **пока упадёт** — нет миграций. Значит, параллельно нужен шаг «Flyway V1__init_schema.sql» для обоих сервисов (см. план из предыдущего сообщения, Блок 1.2).

---

## 9. Дальнейший roadmap (после Docker)

1. **Flyway-миграции** (`V1__init_schema.sql` в обоих сервисах) — таблицы из конспекта TMS.
2. **Health-эндпоинты** + проверка маршрутизации через Gateway.
3. **Опционально:** модуль `tms-common` (общие DTO, исключения, `ErrorResponse`, константы топиков).
4. **Первый вертикальный слайс:** `Project` в Repository, `TestRun` в Execution.
5. **Feign** Execution → Repository (валидация `projectId`).
6. **Kafka:** producer `TestRunFinished` в Execution + consumer в Repository, топик `test-run-events`.
7. **Тесты:** Testcontainers, `application-test.yml`, профиль `it`.
8. **Angular 22 frontend.**
9. **Security:** JWT в Gateway, `@PreAuthorize` в сервисах.
10. **Observability:** Micrometer + Prometheus + Grafana + Tracing.

---

## 10. Опорные соглашения (не забыть)

- Оба бизнес-сервиса **stateless**, масштабируются горизонтально.
- Cross-service — **только UUID, без FK** между сервисами.
- Одна БД, **разные схемы** (`test_repository`, `test_execution`).
- Наружу — **DTO**, не entity.
- Kafka-топик: `test-run-events`, ключ сообщения — `testRunId`.
- Событие: `TestRunFinished` (см. конспект TMS, раздел 5).
- Enum-значения — строго по конспекту (`role`, `status`, `priority`, `severity`).
- Порты фиксированы: 8761 / 8080 / 8081 / 8082 / 5432 / 9092 / 8090 (Kafka UI).

---

## 11. Как продолжить в новом чате

Первое сообщение в новом чате:

> Продолжаем TMS. Стек: Java 21, Spring Boot 4.0.x, Spring Cloud 2025.1.x, Kafka 4.0 KRaft, Postgres 18, Angular 22. Проект — Maven multi-module `tms` из 4 модулей (eureka-server:8761, api-gateway:8080, test-repository-service:8081, test-execution-service:8082), все дочерние от `com.example.tms:tms`. Maven-структура в IDEA настроена корректно. Следующий шаг — `docker-compose.yml` с Postgres 18 (схемы test_repository + test_execution через init.sql), Kafka 4.0 KRaft и Kafka UI на 8090, с healthchecks. Сформируй итоговый compose + init.sql + инструкцию проверки.