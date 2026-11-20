package com.campusflow.service.dto;

import com.campusflow.model.enums.Priority;

import java.util.Objects;

/**
 * Data transfer object for ticket submission.
 */
public record TicketSubmitDto(String title, String description, int categoryId, Priority priority) {

    public TicketSubmitDto {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        title = title.trim();
        description = description.trim();
    }
}
