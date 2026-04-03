package com.fm.be.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for system configuration read/write.
 * Stores key-value configuration entries used for runtime-configurable settings
 * (e.g., tax slabs, rate limits, feature flags).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigDto {

    private Map<String, Object> config;
}
