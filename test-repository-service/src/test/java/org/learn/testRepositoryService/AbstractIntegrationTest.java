package org.learn.testRepositoryService;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * База для всех IT Repo-сервиса.
 *
 * <p>Контейнеры: Postgres 18 + Kafka 4.0 (KRaft, apache/kafka image).
 * Живут на уровне класса — переиспользуются всеми @Test методами одного IT-класса.
 *
 * <p>Профиль: {@code it} → application-it.yml.
 * <p>Схему в Postgres создаёт Flyway при старте (createSchemas=true по умолчанию),
 * init-скрипт не нужен. @DynamicPropertySource перебивает только URL и bootstrap-servers;
 * три места схемы (hikari/default_schema/flyway) уже выставлены в application.yml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("tms")
                    .withUsername("tms")
                    .withPassword("tms");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
