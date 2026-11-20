package com.campusflow.service.dto;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Data transfer object encapsulating aggregated helpdesk metrics.
 */
public record AnalyticsReportDto(Map<String, Integer> ticketsByStatus,
                                 Map<String, Integer> ticketsByCategory,
                                 Map<String, Double> avgResolutionTimeByDept,
                                 Map<String, Integer> openTicketsByStaff,
                                 LocalDateTime generatedAt) {

    public AnalyticsReportDto {
        Objects.requireNonNull(ticketsByStatus, "ticketsByStatus must not be null");
        Objects.requireNonNull(ticketsByCategory, "ticketsByCategory must not be null");
        Objects.requireNonNull(avgResolutionTimeByDept, "avgResolutionTimeByDept must not be null");
        Objects.requireNonNull(openTicketsByStaff, "openTicketsByStaff must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");

        ticketsByStatus = Collections.unmodifiableMap(ticketsByStatus);
        ticketsByCategory = Collections.unmodifiableMap(ticketsByCategory);
        avgResolutionTimeByDept = Collections.unmodifiableMap(avgResolutionTimeByDept);
        openTicketsByStaff = Collections.unmodifiableMap(openTicketsByStaff);
    }
}
