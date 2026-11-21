package com.sowad.nourish.health;

import com.sowad.nourish.health.query.HealthDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthQueryService healthQueryService;

    public HealthController(HealthQueryService healthQueryService) {
        this.healthQueryService = healthQueryService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthDetails> retrieveHealth() {
        HealthDetails health = healthQueryService.retrieveHealth();
        return ResponseEntity.status("ok".equals(health.status()) ? 200 : 503)
                .body(health);
    }
}
