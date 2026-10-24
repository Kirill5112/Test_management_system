-- =========================================================
-- TMS / test_execution schema, V1
-- Таблицы: test_runs, test_results, bug_reports
-- Внешние UUID-поля (project_id, test_case_id, *_by_id) — БЕЗ FK:
-- это cross-service ссылки в другие схемы/сервисы.
-- =========================================================

-- ---------- test_runs ----------
CREATE TABLE test_runs (
                           id             UUID          PRIMARY KEY,
                           project_id     UUID          NOT NULL,          -- ref → test_repository.projects
                           name           VARCHAR(255)  NOT NULL,
                           description    TEXT,
                           status         VARCHAR(30)   NOT NULL,
                           environment    VARCHAR(100),
                           build_version  VARCHAR(100),
                           started_at     TIMESTAMPTZ,
                           finished_at    TIMESTAMPTZ,
                           created_by_id  UUID          NOT NULL,          -- ref → test_repository.users
                           created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                           updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                           CONSTRAINT chk_test_runs_status
                               CHECK (status IN ('CREATED','IN_PROGRESS','COMPLETED','ABORTED'))
);
CREATE INDEX idx_test_runs_project_id    ON test_runs(project_id);
CREATE INDEX idx_test_runs_status        ON test_runs(status);
CREATE INDEX idx_test_runs_created_by_id ON test_runs(created_by_id);

-- ---------- test_results ----------
CREATE TABLE test_results (
                              id             UUID          PRIMARY KEY,
                              test_run_id    UUID          NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
                              test_case_id   UUID          NOT NULL,          -- ref → test_repository.test_cases
                              status         VARCHAR(20)   NOT NULL,
                              comment        TEXT,
                              actual_result  TEXT,
                              duration_ms    BIGINT,
                              executed_by_id UUID,                            -- ref → test_repository.users
                              executed_at    TIMESTAMPTZ,
                              created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                              updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                              CONSTRAINT uq_test_results_run_case UNIQUE (test_run_id, test_case_id),
                              CONSTRAINT chk_test_results_status
                                  CHECK (status IN ('PASSED','FAILED','BLOCKED','SKIPPED'))
);
CREATE INDEX idx_test_results_test_case_id   ON test_results(test_case_id);
CREATE INDEX idx_test_results_executed_by_id ON test_results(executed_by_id);

-- ---------- bug_reports ----------
CREATE TABLE bug_reports (
                             id             UUID          PRIMARY KEY,
                             test_result_id UUID          REFERENCES test_results(id) ON DELETE SET NULL,
                             project_id     UUID          NOT NULL,          -- ref → test_repository.projects
                             title          VARCHAR(255)  NOT NULL,
                             description    TEXT,
                             status         VARCHAR(30)   NOT NULL,
                             severity       VARCHAR(20)   NOT NULL,
                             priority       VARCHAR(20)   NOT NULL,
                             assignee_id    UUID,                            -- ref → test_repository.users
                             reporter_id    UUID          NOT NULL,          -- ref → test_repository.users
                             resolved_at    TIMESTAMPTZ,
                             created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                             updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                             CONSTRAINT chk_bug_reports_status
                                 CHECK (status   IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED','REJECTED')),
                             CONSTRAINT chk_bug_reports_severity
                                 CHECK (severity IN ('BLOCKER','CRITICAL','MAJOR','MINOR','TRIVIAL')),
                             CONSTRAINT chk_bug_reports_priority
                                 CHECK (priority IN ('HIGH','MEDIUM','LOW'))
);
CREATE INDEX idx_bug_reports_test_result_id ON bug_reports(test_result_id);
CREATE INDEX idx_bug_reports_project_id     ON bug_reports(project_id);
CREATE INDEX idx_bug_reports_status         ON bug_reports(status);
CREATE INDEX idx_bug_reports_assignee_id    ON bug_reports(assignee_id);