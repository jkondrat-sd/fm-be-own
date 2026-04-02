package com.fm.be.modules.employees.dto;

import com.fm.be.common.constants.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for employee document data included in employee responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDocumentDto {

    private UUID id;
    private DocumentType type;
    private String fileUrl;
    private boolean isVerified;
    private UUID verifiedBy;
    private Instant verifiedAt;
    private Instant createdAt;
}
