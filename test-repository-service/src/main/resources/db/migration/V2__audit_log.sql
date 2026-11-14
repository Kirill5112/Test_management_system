-- test_repository.audit_log — журнал входящих Kafka-событий.
-- PK генерируется приложением (UUID), DEFAULT NOW() — только страховка
-- на случай прямых SQL-инсертов; Hibernate всё равно проставит received_at.

CREATE TABLE IF NOT EXISTS test_repository.audit_log
(
    id           UUID PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    aggregate_id UUID        NOT NULL,
    project_id   UUID        NOT NULL,
    payload      JSONB       NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Идемпотентность: одно событие одного типа для одного агрегата — ровно один раз.
-- Это защита от Kafka at-least-once и от ребалансов.
CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_log_event_aggregate
    ON test_repository.audit_log (event_type, aggregate_id);

-- Типовые запросы журнала: по проекту, по времени.
CREATE INDEX IF NOT EXISTS idx_audit_log_project_id
    ON test_repository.audit_log (project_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_received_at
    ON test_repository.audit_log (received_at);