package com.fm.be.modules.employees.dto;

import com.fm.be.common.constants.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for employee data.
 * Includes user info and excludes encrypted PII fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponseDto {

    private UUID id;
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private Role role;
    private LocalDate dob;
    private UUID siteId;
    private UUID organizationId;
    private boolean hasFaceRegistered;
    private List<EmployeeDocumentDto> documents;
    private Instant createdAt;
    private Instant updatedAt;
}
