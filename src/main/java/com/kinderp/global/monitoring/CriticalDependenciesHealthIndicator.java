package com.kinderp.global.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("criticalDependencies")
public class CriticalDependenciesHealthIndicator implements HealthIndicator {

    private static final int DATABASE_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public CriticalDependenciesHealthIndicator(DataSource dataSource,
                                               RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        DependencyStatus database = probeDatabase();
        DependencyStatus redis = probeRedis();

        boolean allUp = database.isUp() && redis.isUp();
        Health.Builder builder = allUp ? Health.up() : Health.down();

        return builder
                .withDetail("database", database.toDetail())
                .withDetail("redis", redis.toDetail())
                .build();
    }

    private DependencyStatus probeDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(DATABASE_VALIDATION_TIMEOUT_SECONDS)) {
                return DependencyStatus.up("connection-valid");
            }
            return DependencyStatus.down("connection-invalid");
        } catch (Exception ex) {
            return DependencyStatus.down(ex.getClass().getSimpleName());
        }
    }

    private DependencyStatus probeRedis() {
        if (redisConnectionFactory == null) {
            return DependencyStatus.down("connection-factory-missing");
        }

        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return DependencyStatus.up("pong");
            }
            return DependencyStatus.down(pong == null ? "empty-ping-response" : pong);
        } catch (Exception ex) {
            return DependencyStatus.down(ex.getClass().getSimpleName());
        }
    }

    private record DependencyStatus(String status, String detail) {

        static DependencyStatus up(String detail) {
            return new DependencyStatus("UP", detail);
        }

        static DependencyStatus down(String detail) {
            return new DependencyStatus("DOWN", detail);
        }

        boolean isUp() {
            return "UP".equals(status);
        }

        Map<String, Object> toDetail() {
            Map<String, Object> detailMap = new LinkedHashMap<>();
            detailMap.put("status", status);
            detailMap.put("detail", detail);
            return detailMap;
        }
    }
}
