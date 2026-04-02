package com.fm.be.modules.sites.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for updating an existing site.
 * All fields are optional — only non-null fields are updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSiteDto {

    /** Update site name */
    private String name;

    /** Update address */
    private String address;

    /** Update active status */
    private Boolean isActive;

    /** Update client assignment */
    private UUID clientId;
}
