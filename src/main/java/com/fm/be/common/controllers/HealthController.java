package com.fm.be.common.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/health")
public class HealthController {

    @GetMapping
    public Map<String, String> getHealth() {
        return Map.of("status", "UP", "message", "Facility Management Backend Service is running");
    }
}
