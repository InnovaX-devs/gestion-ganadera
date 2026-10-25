package com.innovax.ganadera.common.health;

import java.sql.Connection;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    public record HealthResponse(String status, String database, Instant timestamp) {
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp = isDatabaseUp();
        HealthResponse body = new HealthResponse(dbUp ? "UP" : "DOWN", dbUp ? "UP" : "DOWN", Instant.now());
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
