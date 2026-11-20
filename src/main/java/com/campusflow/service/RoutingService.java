package com.campusflow.service;

import com.campusflow.model.Category;
import com.campusflow.model.Department;
import com.campusflow.model.Ticket;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.service.exception.ResourceNotFoundException;
import com.campusflow.service.exception.ValidationException;

import java.util.Objects;

/**
 * Service for dynamically resolving complaint categories to target departments (FR-05, UC-02).
 */
public class RoutingService {

    private final CategoryRepository categoryRepo;
    private final DepartmentRepository departmentRepo;

    public RoutingService(CategoryRepository categoryRepo, DepartmentRepository departmentRepo) {
        this.categoryRepo = Objects.requireNonNull(categoryRepo, "categoryRepo must not be null");
        this.departmentRepo = Objects.requireNonNull(departmentRepo, "departmentRepo must not be null");
    }

    /**
     * Resolves the target department for the given ticket based on its category ID.
     *
     * @param ticket the ticket to route
     * @return target Department
     * @throws ValidationException if the ticket's category is inactive
     * @throws ResourceNotFoundException if the category or target department does not exist
     */
    public Department routeTicket(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        return getDepartmentForCategory(ticket.getCategoryId());
    }

    /**
     * Resolves the target department for the given category ID.
     *
     * @param categoryId the category database primary key
     * @return target Department
     * @throws ValidationException if the category is inactive
     * @throws ResourceNotFoundException if the category or target department does not exist
     */
    public Department getDepartmentForCategory(int categoryId) {
        Category category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));

        if (!category.isActive()) {
            throw new ValidationException("Cannot route ticket to inactive category: " + category.getName());
        }

        return departmentRepo.findById(category.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with ID: " + category.getDepartmentId()));
    }
}
