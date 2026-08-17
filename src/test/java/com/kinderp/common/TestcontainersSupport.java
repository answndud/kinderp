package com.kinderp.common;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 전용 MySQL/Redis 컨테이너를 기동하고 Spring 속성으로 연결한다.
 * 통합 테스트가 H2/Mock 대신 운영과 유사한 인프라를 사용하도록 맞춘다.
 */
public abstract class TestcontainersSupport {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0.36");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("erp_test")
            .withUsername("erp")
            .withPassword("erp")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--default-time-zone=+09:00"
            );

    protected static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no");

    static {
        Startables.deepStart(MYSQL, REDIS).join();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
