# Конспект 5 (новый): Инициализация `tms-frontend` (Angular) и текущее состояние

> Срез состояния фронтенда после инициализации проекта и первого прогона. Продолжение Документа 3 (roadmap: «следующий шаг — Angular frontend»). Всё, что реализовано в бэкенде, описано в Документах 1–3; правила — в Документе 2.

---

## 1. Создание проекта

- CLI: `ng new tms-frontend --style=scss` (standalone-приложение, без NgModule).
- Версии (зафиксированы в `package.json`, не менять без причины):
  - Angular **22.2.x** (`@angular/core`, `router`, `forms`, `http`, `cdk`, `material`)
  - NgRx **22.0.x**: `@ngrx/store`, `@ngrx/effects`, `@ngrx/entity`, `@ngrx/store-devtools` (все — functional-стиль, без классовых Modules/Effects)
  - TypeScript `~6.0`, RxJS `~7.8`, сборка через `@angular/build:application` (esbuild), тесты — `vitest` + `jsdom`
- Требование к окружению: Node.js ≥ v22.22.3 (проверять при «пустой странице» / падениях dev-server).

## 2. Структура (текущая)

```
tms-frontend/
├── proxy.conf.json                  ← /api → http://localhost:8080 (gateway), changeOrigin, debug
├── angular.json                     ← serve.options.proxyConfig = proxy.conf.json; styles: [src/styles.scss]
└── src/
    ├── environments/environment.ts  ← apiUrl: '/api' (относительный, идёт через proxy)
    └── app/
        ├── main.ts                  ← bootstrapApplication(App, appConfig)
        ├── app.config.ts            ← провайдеры (см. §3)
        ├── app.routes.ts            ← MainLayout + lazy 'projects'; 'test-runs'/'test-results' закомментированы
        ├── app.html                 ← ⚠ до сих пор содержит placeholder ng new + <router-outlet />
        ├── core/
        │   ├── api/project.service.ts        ← CRUD против /api/repository/projects
        │   ├── interceptors/error.interceptor.ts ← catchError → MatSnackBar (message или «Сервер недоступен» при status=0) → rethrow
        │   └── models/                        ← project.model.ts, page-response.model.ts, error-response.model.ts (зеркало tms-common DTO)
        ├── features/projects/
        │   ├── projects.routes.ts             ← list + form (new/:id/edit), lazy
        │   ├── project-list/ , project-form/  ← Material-таблица + reactive form
        │   └── store/                          ← actions / reducer(@ngrx/entity) / effects(functional) / selectors
        └── layout/main-layout/                 ← mat-sidenav-обёртка
```

## 3. App config (`app.config.ts`)

```ts
provideZoneChangeDetection({ eventCoalescing: true }),
provideRouter(routes),
provideHttpClient(withInterceptors([errorInterceptor])),
provideAnimationsAsync(),
provideStore({}),            // feature-сторы подключаются через provideState в маршрутах
provideEffects([]),
provideStoreDevtools({ maxAge: 25, logOnly: !isDevMode() }),
```

⚠ Приложение **зоновое** (`provideZoneChangeDetection`). При инициализации была ошибка **NG0908: Angular requires Zone.js** — лечится либо добавлением `"node_modules/zone.js"` в `scripts`/импортом в `main.ts`, либо переходом на zoneless (`provideZonelessChangeDetection()` и удалить `provideZoneChangeDetection`). Сейчас работает — зона подключена корректно.

## 4. Интеграция с бэкендом

- Путь запроса: `/api/repository/projects` → proxy (dev-server) → Gateway `localhost:8080` → StripPrefix=2 → `test-repository-service` `/projects`.
- `ProjectService`: `list(query{page,size,sort,status,ownerId})`, `getById`, `getByKey`, `create`, `update`, `delete` — типизированы через `PageResponse<Project>` / `Project*Request` из `core/models` (зеркало контрактов tms-common).
- NgRx-эффекты (functional): `loadProjects$` (sort=`createdAt,desc`), `createProject$`, `updateProject$`, `deleteProject$`; ошибки → `*Failure({error: code ?? 'UNKNOWN'})` (код ошибки из `ErrorResponse.code`, словарь — Документ 2).

## 5. Проблемы, найденные при первом запуске (и их причины)

| Симптом | Причина | Статус |
|---|---|---|
| Пустая страница, нет запросов в Network | В `app.html` остался placeholder `ng new`, а роутинг не рендерился; плюс пустой `styles.scss` | Частично: запросы пошли, но `app.html` **всё ещё содержит шаблонный блок** — его надо заменить на один `<router-outlet />` |
| NG0908 Zone.js | `provideZoneChangeDetection` без подключённого zone.js | Решено (см. §3) |
| Material-компоненты без стилей | Не подключена prebuilt-тема Angular Material и шрифт Roboto (`styles.scss` пуст, в `angular.json` только он) | **Открыто**: добавить `@use '@angular/material' as mat; … @include mat.all-component-themes()` или prebuilt CSS в `angular.json` |

## 6. Замечания code review (текущий долг)

1. `app.html` — удалить placeholder, оставить `<router-outlet />`.
2. Тема Material + Roboto не подключены (§5).
3. `update()` в сервисе отправляет тело с полем `key`, которого нет в backend `ProjectUpdateRequest`; regex фронтового `key` строже бэкендовского — унифицировать с контрактом.
4. После dispatch create/delete — оптимистичный `navigate`/нет перезагрузки списка; лучше реагировать на `*Success` (и рассмотреть `exhaustMap` вместо `switchMap` для mutate-эффектов).
5. Дублирование обработки ошибок: interceptor показывает message, эффекты кладут code; `selectProjectsError` не используется.
6. Paginator: размер захардкожен, не синхронизирован со стором.
7. Мелочи: нет UUID-валидатора `ownerId`, `getByKey` не используется, избыточный `standalone: true` (Angular 22 — standalone by default), README от CLI, нет ESLint.
8. Маршруты `test-runs` / `test-results` закомментированы — фичи ещё не созданы.

## 7. Что дальше

- Пункты 1–2 из §6 (чистый `app.html`, тема) — минимальный минимум для визуальной приёмки.
- Feature `test-runs` (+ позже `test-results`) по образцу `projects`: routes + service + NgRx-store + Material-таблица.
- Синхронизация контрактов с бэкендом (поля update-запроса, словари enum'ов из tms-common).
- Интеграционное тестирование фронта с поднятым docker-compose (Eureka + Gateway + repository-service); тесты — vitest (пока написан только boilerplate `app.spec.ts`).
