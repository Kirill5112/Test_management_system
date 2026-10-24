-- TMS: bootstrap-скрипт. Выполняется один раз — при первом старте контейнера

CREATE SCHEMA IF NOT EXISTS test_repository;
CREATE SCHEMA IF NOT EXISTS test_execution;

-- Явные гранты (не обязательны, но полезны, если позже появится отдельная роль):
GRANT ALL ON SCHEMA test_repository TO tms;
GRANT ALL ON SCHEMA test_execution TO tms;