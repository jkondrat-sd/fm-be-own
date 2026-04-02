package com.fm.be.modules.employees;

import com.fm.be.modules.employees.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the employee_documents table.
 */
@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

    /**
     * Finds all documents for a specific employee.
     *
     * @param employeeId the employee's UUID
     * @return list of documents
     */
    List<EmployeeDocument> findByEmployeeId(UUID employeeId);
}
