-- =========================================================
-- TMS / test_repository schema, V1
-- Таблицы: users, projects, project_members, test_suites, test_cases
-- =========================================================

-- ---------- users ----------
CREATE TABLE users (
                       id            UUID          PRIMARY KEY,
                       username      VARCHAR(50)   NOT NULL,
                       email         VARCHAR(255)  NOT NULL,
                       password_hash VARCHAR(255)  NOT NULL,
                       first_name    VARCHAR(100),
                       last_name     VARCHAR(100),
                       role          VARCHAR(30)   NOT NULL,
                       active        BOOLEAN       NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                       CONSTRAINT uq_users_username UNIQUE (username),
                       CONSTRAINT uq_users_email    UNIQUE (email),
                       CONSTRAINT chk_users_role    CHECK (role IN ('ADMIN','MANAGER','TESTER','DEVELOPER'))
);

-- ---------- projects ----------
CREATE TABLE projects (
                          id          UUID          PRIMARY KEY,
                          key         VARCHAR(20)   NOT NULL,
                          name        VARCHAR(255)  NOT NULL,
                          description TEXT,
                          owner_id    UUID          NOT NULL REFERENCES users(id),
                          status      VARCHAR(30)   NOT NULL,
                          created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                          updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                          CONSTRAINT uq_projects_key UNIQUE (key),
                          CONSTRAINT chk_projects_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_projects_owner_id ON projects(owner_id);

-- ---------- project_members (M:N User<->Project) ----------
CREATE TABLE project_members (
                                 project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                                 user_id         UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
                                 role_in_project VARCHAR(30)  NOT NULL,
                                 PRIMARY KEY (project_id, user_id),
                                 CONSTRAINT chk_project_members_role
                                     CHECK (role_in_project IN ('MANAGER','TESTER','DEVELOPER'))
);
CREATE INDEX idx_project_members_user_id ON project_members(user_id);

-- ---------- test_suites ----------
CREATE TABLE test_suites (
                             id              UUID          PRIMARY KEY,
                             project_id      UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
                             parent_suite_id UUID          REFERENCES test_suites(id)       ON DELETE CASCADE,
                             name            VARCHAR(255)  NOT NULL,
                             description     TEXT,
                             position        INT,
                             created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                             updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_test_suites_project_parent ON test_suites(project_id, parent_suite_id);
CREATE INDEX idx_test_suites_project_name   ON test_suites(project_id, name);

-- ---------- test_cases ----------
CREATE TABLE test_cases (
                            id              UUID          PRIMARY KEY,
                            suite_id        UUID          NOT NULL REFERENCES test_suites(id) ON DELETE CASCADE,
                            title           VARCHAR(255)  NOT NULL,
                            description     TEXT,
                            preconditions   TEXT,
                            steps           JSONB,
                            expected_result TEXT,
                            priority        VARCHAR(20)   NOT NULL,
                            type            VARCHAR(20)   NOT NULL,
                            status          VARCHAR(20)   NOT NULL,
                            created_by_id   UUID          REFERENCES users(id),
                            created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                            updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                            CONSTRAINT chk_test_cases_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
                            CONSTRAINT chk_test_cases_type     CHECK (type     IN ('MANUAL','AUTOMATED')),
                            CONSTRAINT chk_test_cases_status   CHECK (status   IN ('DRAFT','ACTIVE','DEPRECATED'))
);
CREATE INDEX idx_test_cases_suite_id      ON test_cases(suite_id);
CREATE INDEX idx_test_cases_created_by_id ON test_cases(created_by_id);