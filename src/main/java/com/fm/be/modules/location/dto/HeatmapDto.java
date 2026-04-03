package com.fm.be.modules.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for heatmap response data from aggregated location history.
 * Each point represents a GPS coordinate with a density weight.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapDto {

    private List<HeatmapPoint> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapPoint {
        private Double latitude;
        private Double longitude;
        /** Weight/density at this point */
        private int weight;
    }
}
