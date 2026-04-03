package com.fm.be.modules.admin;

import com.fm.be.modules.admin.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the organizations table.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
