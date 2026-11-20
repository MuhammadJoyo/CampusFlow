package com.campusflow.service;

import com.campusflow.model.Category;
import com.campusflow.model.Department;
import com.campusflow.model.enums.Role;
import com.campusflow.repository.AuditLogRepository;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.service.exception.ResourceNotFoundException;
import com.campusflow.service.exception.ValidationException;
import com.campusflow.util.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for category and department administration (FR-16, UC-12, UC-13).
 */
public class CategoryService {

    private final CategoryRepository categoryRepo;
    private final DepartmentRepository deptRepo;
    private final AuditLogRepository auditRepo;

    public CategoryService(CategoryRepository categoryRepo,
                           DepartmentRepository deptRepo,
                           AuditLogRepository auditRepo) {
        this.categoryRepo = Objects.requireNonNull(categoryRepo, "categoryRepo must not be null");
        this.deptRepo = Objects.requireNonNull(deptRepo, "deptRepo must not be null");
        this.auditRepo = Objects.requireNonNull(auditRepo, "auditRepo must not be null");
    }

    /**
     * Creates a new complaint category linked to a department.
     *
     * @param adminSession authenticated administrator session
     * @param name         category name
     * @param description  category description
     * @param departmentId owning department ID
     * @return persisted Category instance
     */
    public Category createCategory(Session adminSession, String name, String description, int departmentId) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);

        if (name == null || name.isBlank()) {
            throw new ValidationException("Category name must not be blank.");
        }

        // Verify department exists
        Department dept = deptRepo.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + departmentId));

        Category category = new Category(0, name.trim(), description != null ? description.trim() : "", dept.getId(), true);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Category saved = categoryRepo.save(category, conn);
                auditRepo.log(adminSession.getUserId(), "CREATE", "CATEGORY", saved.getId(),
                        "Created category '" + saved.getName() + "' under department " + dept.getName(), conn);
                conn.commit();
                return saved;
            } catch (SQLException e) {
                conn.rollback();
                throw new ValidationException("Failed to create category: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection error: " + e.getMessage());
        }
    }

    /**
     * Deactivates a category so it cannot be used for new ticket submissions.
     *
     * @param adminSession authenticated administrator session
     * @param categoryId   category ID to deactivate
     */
    public void deactivateCategory(Session adminSession, int categoryId) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);

        Category category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                categoryRepo.setActive(categoryId, false, conn);
                auditRepo.log(adminSession.getUserId(), "DEACTIVATE", "CATEGORY", categoryId,
                        "Deactivated category '" + category.getName() + "'", conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new ValidationException("Failed to deactivate category: " + e.getMessage());
            }
        } catch (SQLException e) {
            throw new ValidationException("Database connection error: " + e.getMessage());
        }
    }

    public List<Category> getAllActiveCategories() {
        return categoryRepo.findAllActive();
    }

    public List<Category> getCategoriesByDepartment(int departmentId) {
        return categoryRepo.findByDepartment(departmentId);
    }

    public List<Department> getAllDepartments() {
        return deptRepo.findAll();
    }

    public Optional<Department> getDepartmentById(int departmentId) {
        return deptRepo.findById(departmentId);
    }
}
