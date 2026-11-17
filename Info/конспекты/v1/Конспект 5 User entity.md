# Конспект 5: Вертикальный слайс `User` в `test-repository-service` (готово)

## 1. Итоговое состояние

| Что | Статус |
|---|---|
| Модуль `tms-common` | ✅ Готов (DTO, `ErrorResponse`, `PageResponse`, enum'ы, `GlobalExceptionHandler`, `KafkaTopics`, `TestRunFinishedEvent`, `CommonAutoConfiguration`) |
| Слайс `Project` (Repository) | ✅ CRUD, пагинация, фильтрация, MapStruct, валидация |
| **Слайс `User` (Repository)** | ✅ **Готов**: Entity, Repo, Mapper, Service, Controller, DTO, BCrypt, soft-delete |
| `spring-security-crypto` в `pom.xml` Repository | ✅ Добавлен (только крипто, **без** Security авто-конфига) |
| Все smoke-тесты через Postman/curl | ✅ Проходят (201/404/409/204) |
| Пакет проекта | `org.learn` (учтено везде) |

**Порты:** 8761 Eureka, 8080 Gateway, 8081 Repo, 8082 Exec, 5432 Postgres, 9092 Kafka, 8090 Kafka UI.

---

## 2. Что реализовано в слайсе `User`

### 2.1. Файлы (все под `test-repository-service/src/main/java/org/learn/repository/`)

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

### 2.2. Эндпоинты (через Gateway → `/api/repository/users`)

| Метод | Путь | Статус | Назначение |
|---|---|---|---|
| POST | `/users` | 201 | создание, `Location` header |
| GET | `/users?page=&size=&sort=` | 200 | пагинация (`@PageableDefault(size=20, sort="createdAt")`) |
| GET | `/users/{id}` | 200 / 404 | по UUID |
| GET | `/users/by-username/{username}` | 200 / 404 | по username |
| PUT | `/users/{id}` | 200 / 404 / 409 | полная замена (без пароля) |
| PATCH | `/users/{id}/password` | 204 / 404 | смена пароля |
| DELETE | `/users/{id}` | 204 / 404 | **soft-delete** (`active=false`) |

### 2.3. Ключевые решения

- **`passwordHash` — `BCryptPasswordEncoder`**, поле `VARCHAR(255)`, хэш 60 символов укладывается с запасом.
- **Soft-delete** через `active=false`. Hard-delete невозможен: `users.id` держат FK из `projects.owner_id`, `test_cases.created_by_id`, `project_members.user_id`.
- **`Project.ownerId` остаётся скалярным `UUID`** — cross-slice связи только по UUID, `@ManyToOne` не вводим.
- **Пароль никогда не отдаётся наружу** — в `UserResponse` поля `passwordHash` нет.

---

## 3. ⚠️ ПРАВИЛА ПРОЕКТА (не повторять ошибок)

### 3.1. Сигнатуры исключений — **два аргумента `(code, message)`**

Все исключения из `org.learn.exception`:

```java
new ResourceNotFoundException(String code, String message)  // → 404
new ConflictException(String code, String message)          // → 409
new BadRequestException(String code, String message)        // → 400
```

**`code`** — машиночитаемый, `SCREAMING_SNAKE_CASE`, с префиксом сущности. **`message`** — для человека.

**Словарь кодов, используемых сейчас:**

| code | status | источник |
|---|---|---|
| `USER_NOT_FOUND` | 404 | `UserService.findById/getByUsername` |
| `USERNAME_ALREADY_EXISTS` | 409 | `UserService.create/update` |
| `EMAIL_ALREADY_EXISTS` | 409 | `UserService.create/update` |
| `PROJECT_NOT_FOUND` | 404 | `ProjectService` |
| `PROJECT_KEY_ALREADY_EXISTS` | 409 | `ProjectService` |

**Правило на будущее:** при добавлении нового слайса сначала пополняем этот словарь кодами `<ENTITY>_NOT_FOUND`, `<FIELD>_ALREADY_EXISTS`, `<ENTITY>_INVALID_STATE` и т.д.

### 3.2. Защита от `null` в обязательных полях — **три уровня**

Симптом-триггер: `ERROR: null value in column "active" of relation "users" violates not-null constraint`.

Причина: `@Builder.Default` **не** спасает, когда MapStruct / Jackson проставили `null` явно. Поле «трогали» — default не применился.

**Паттерн (применять ко всем полям с DB-`NOT NULL` + DB `DEFAULT`):**

1. **Mapper** — `@Mapping(target = "<field>", source = "<field>", defaultValue = "true")` (или `"ACTIVE"` для enum'ов, `"0"` для чисел).
2. **Entity** — `@PrePersist` хук:
   ```java
   @PrePersist
   void prePersist() {
       if (active == null) active = Boolean.TRUE;
   }
   ```
3. **Service** — явная страховка сразу после маппинга:
   ```java
   if (user.getActive() == null) user.setActive(Boolean.TRUE);
   ```

Одного уровня хватило бы, но держим все три — дёшево и защищает от регрессий при регенерации mapper'а или смене версии Lombok/MapStruct.

**Исключение:** поля с `@NotNull` в DTO (например, `UserUpdateRequest.active`) — валидация отсечёт `null` до маппера, им хватит только `@NotNull`.

### 3.3. MapStruct — обязательные `@Mapping(target=..., ignore=true)`

Для всех полей, которые не должны мапиться напрямую из request'а:
- `id`, `passwordHash`, `createdAt`, `updatedAt` — всегда `ignore = true`.
- `passwordHash` ставится в сервисе после `passwordEncoder.encode(...)`, никогда в маппере.

### 3.4. `spring-security-crypto` vs `spring-boot-starter-security`

Только `spring-security-crypto` (даёт `BCryptPasswordEncoder`, `PasswordEncoder`). **Не** `-starter-security` — иначе включится авто-конфиг Security и все эндпоинты закроются за HTTP Basic.

---

## 4. Быстрая проверка после старта (smoke)

```bash
# 1) без active → active=true
curl -i -X POST http://localhost:8080/api/repository/users \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","email":"bob@example.com","password":"S3cretPass!","role":"TESTER"}'

# 2) дубликат username → 409 code=USERNAME_ALREADY_EXISTS
# 3) несуществующий id → 404 code=USER_NOT_FOUND
# 4) в БД hash_len ≈ 60 (BCrypt), active не NULL
docker exec -it tms-postgres psql -U tms -d tms \
  -c "SELECT username, active, length(password_hash) FROM test_repository.users;"

# 5) теперь Project с реальным ownerId создаётся без 409
curl -i -X POST http://localhost:8080/api/repository/projects \
  -H "Content-Type: application/json" \
  -d '{"key":"DEMO","name":"Demo","ownerId":"<uuid-bob>","status":"ACTIVE"}'
```

---

## 5. Roadmap — что дальше

По порядку из предыдущих конспектов, мы здесь:

1. ✅ `tms-common`
2. ✅ `Project` (Repository)
3. ✅ **`User` (Repository)** ← текущая точка
4. ⏭️ **`TestRun` (Execution Service)** — entity → repo → service → controller → DTO, статусы CREATED/IN_PROGRESS/COMPLETED/ABORTED
5. ⏭️ **Feign Execution → Repository** — валидация `projectId` при создании `TestRun`
6. ⏭️ **Kafka**: producer `TestRunFinished` в Exec (при `IN_PROGRESS → COMPLETED`) + consumer в Repo, топик `test-run-events`, ключ `testRunId`
7. ⏭️ `TestResult`, `BugReport` (Execution)
8. ⏭️ `ProjectMember`, `TestSuite`, `TestCase` (Repository)
9. ⏭️ Тесты (Testcontainers, профиль `it`, `application-test.yml`)
10. ⏭️ Angular 22 frontend
11. ⏭️ Security (JWT в Gateway, `@PreAuthorize`)
12. ⏭️ Observability (Micrometer + Prometheus + Grafana + Tracing)

---

## 6. Соглашения проекта (сводка, не забывать)

- **Stateless**-микросервисы, горизонтальное масштабирование.
- **Cross-service — только UUID, без FK**. Валидация ссылок на уровне приложения (Feign).
- Одна БД `tms`, **разные схемы** `test_repository` / `test_execution`.
- Наружу — **DTO**, не entity.
- Время в entity — `OffsetDateTime` (под `TIMESTAMPTZ`).
- `ddl-auto: validate`; схему меняет только Flyway.
- Enum-значения — строго по Конспекту 1, продублированы в `CHECK`-констрейнтах БД.
- Kafka-топик: `test-run-events`, ключ — `testRunId`.
- Исключения: **`(code, message)`** — см. §3.1.
- Обязательные поля с DB default: **три уровня защиты от `null`** — см. §3.2.
- MapStruct: игнорировать `id`, `passwordHash`, `createdAt`, `updatedAt` в мапперах на create/update.

---

## 7. Как продолжить в новом чате

**Первое сообщение:**

> Продолжаем TMS. Стек: Java 21, Spring Boot 4.0.x, Spring Cloud 2025.1.x, Kafka 4.0 KRaft, Postgres 18, Angular 22. Maven multi-module `tms`, пакет `org.learn`, все дочерние от `com.example.tms:tms`. Порты: 8761/8080/8081/8082.
>
> **Инфраструктура рабочая:** docker compose поднимает Postgres 18 (схемы `test_repository` + `test_execution` через `init.sql`) + Kafka 4.0 KRaft + Kafka UI 8090. Flyway V1 применён в обе схемы, все 4 сервиса стартуют, Eureka видит 3 приложения, Gateway маршрутизирует `/api/repository/**` и `/api/execution/**`.
>
> **Готово:**
> - `tms-common`: DTO (`ErrorResponse`, `PageResponse`), enum'ы, `GlobalExceptionHandler` (подключён через `@Import(CommonAutoConfiguration.class)` в главных классах), `KafkaTopics`, `TestRunFinishedEvent`.
> - Слайс `Project` в Repository (полный CRUD + валидация).
> - Слайс `User` в Repository (полный CRUD, BCrypt, soft-delete, все smoke-тесты проходят).
>
> **Правила проекта (не повторять ошибки):**
> 1. Все исключения `org.learn.exception` принимают **два аргумента** `(String code, String message)`. Коды — `SCREAMING_SNAKE_CASE` с префиксом сущности (`USER_NOT_FOUND`, `USERNAME_ALREADY_EXISTS`, …).
> 2. Для полей с DB `NOT NULL` + `DEFAULT` — **три уровня защиты от null**: `@Mapping(defaultValue=…)` в MapStruct, `@PrePersist` в entity, явная проверка в service.
> 3. MapStruct: `@Mapping(ignore=true)` для `id`, `passwordHash`, `createdAt`, `updatedAt`; `passwordHash` ставится только в сервисе.
> 4. Только `spring-security-crypto`, не `-starter-security`.
>
> **Следующий шаг:** вертикальный слайс `TestRun` в `test-execution-service` (entity → repo → service → controller → DTO), затем Feign Execution → Repository для валидации `projectId` при создании прогона, затем Kafka producer `TestRunFinished`.
>
> Готов формировать `TestRun`.

---

Готов приступать к слайсу `TestRun` по этой отправной точке.