package com.fm.be.modules.employees;

import com.fm.be.modules.employees.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the employees table.
 * Provides custom queries for site-scoped and org-scoped lookups.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /**
     * Finds an employee by the associated user ID.
     *
     * @param userId the user's UUID
     * @return the employee if found
     */
    Optional<Employee> findByUserId(UUID userId);

    /**
     * Checks if an employee exists for a given user ID.
     *
     * @param userId the user's UUID
     * @return true if an employee record exists
     */
    boolean existsByUserId(UUID userId);

    /**
     * Finds all employees assigned to a specific site (paginated).
     *
     * @param siteId the site's UUID
     * @param pageable pagination parameters
     * @return paginated employees
     */
    Page<Employee> findBySiteId(UUID siteId, Pageable pageable);

    /**
     * Finds all employees in an organization (paginated).
     *
     * @param organizationId the organization's UUID
     * @param pageable pagination parameters
     * @return paginated employees
     */
    Page<Employee> findByOrganizationId(UUID organizationId, Pageable pageable);

    /**
     * Searches employees by name (case-insensitive) within an organization.
     *
     * @param namePattern the search pattern (wrapped with % for LIKE)
     * @param organizationId the organization UUID
     * @param pageable pagination parameters
     * @return matching employees
     */
    @Query("SELECT e FROM Employee e WHERE LOWER(e.fullName) LIKE LOWER(:namePattern) "
            + "AND e.organizationId = :organizationId")
    Page<Employee> searchByName(String namePattern, UUID organizationId,
                                Pageable pageable);

    /**
     * Counts employees assigned to a specific site.
     *
     * @param siteId the site UUID
     * @return count of employees at the site
     */
    long countBySiteId(UUID siteId);

    /**
     * Finds all employees assigned to a specific site (unpaginated).
     *
     * @param siteId the site UUID
     * @return list of employees at the site
     */
    List<Employee> findBySiteId(UUID siteId);
}
