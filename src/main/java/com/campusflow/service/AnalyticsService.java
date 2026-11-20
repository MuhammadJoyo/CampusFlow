package com.campusflow.service;

import com.campusflow.model.Category;
import com.campusflow.model.Department;
import com.campusflow.model.Ticket;
import com.campusflow.model.User;
import com.campusflow.model.enums.Role;
import com.campusflow.model.enums.TicketStatus;
import com.campusflow.repository.CategoryRepository;
import com.campusflow.repository.DepartmentRepository;
import com.campusflow.repository.TicketRepository;
import com.campusflow.repository.UserRepository;
import com.campusflow.service.dto.AnalyticsReportDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Service for computing and caching helpdesk analytics reports (FR-13, ADR-010, UC-14).
 *
 * <p>Protected by a {@link ReentrantReadWriteLock} ensuring thread-safe concurrent reads
 * and exclusive cache invalidation upon ticket lifecycle mutations.</p>
 */
public class AnalyticsService {

    private final TicketRepository ticketRepo;
    private final CategoryRepository categoryRepo;
    private final DepartmentRepository deptRepo;
    private final UserRepository userRepo;

    private final ReentrantReadWriteLock rwLock;
    private final Lock readLock;
    private final Lock writeLock;

    private AnalyticsReportDto cachedReport;
    private LocalDateTime cacheExpiry;

    public AnalyticsService(TicketRepository ticketRepo,
                            CategoryRepository categoryRepo,
                            DepartmentRepository deptRepo,
                            UserRepository userRepo) {

        this.ticketRepo = Objects.requireNonNull(ticketRepo, "ticketRepo must not be null");
        this.categoryRepo = Objects.requireNonNull(categoryRepo, "categoryRepo must not be null");
        this.deptRepo = Objects.requireNonNull(deptRepo, "deptRepo must not be null");
        this.userRepo = Objects.requireNonNull(userRepo, "userRepo must not be null");

        this.rwLock = new ReentrantReadWriteLock();
        this.readLock = rwLock.readLock();
        this.writeLock = rwLock.writeLock();
        this.cacheExpiry = LocalDateTime.MIN;
    }

    /**
     * Retrieves the analytics report, computing from database only if cache is expired or invalid.
     *
     * @param adminSession authenticated administrator session
     * @return aggregated AnalyticsReportDto
     */
    public AnalyticsReportDto getReport(Session adminSession) {
        Objects.requireNonNull(adminSession, "adminSession must not be null");
        adminSession.requireRole(Role.ADMIN);

        readLock.lock();
        try {
            if (cachedReport != null && LocalDateTime.now().isBefore(cacheExpiry)) {
                return cachedReport;
            }
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            // Double-checked locking
            if (cachedReport != null && LocalDateTime.now().isBefore(cacheExpiry)) {
                return cachedReport;
            }
            cachedReport = computeReport();
            cacheExpiry = LocalDateTime.now().plusMinutes(15);
            return cachedReport;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Invalidates the cached metrics report.
     * Invoked by TicketService whenever a ticket is submitted, assigned, escalated, resolved, or closed.
     */
    public void invalidateCache() {
        writeLock.lock();
        try {
            cachedReport = null;
            cacheExpiry = LocalDateTime.MIN;
        } finally {
            writeLock.unlock();
        }
    }

    private AnalyticsReportDto computeReport() {
        List<Ticket> tickets = ticketRepo.findAll();
        List<Category> categories = categoryRepo.findAll();
        List<Department> departments = deptRepo.findAll();
        List<User> users = userRepo.findAll();

        Map<Integer, String> categoryNames = categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        Map<Integer, Integer> categoryDepts = categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getDepartmentId, (a, b) -> a));

        Map<Integer, String> departmentNames = departments.stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        Map<Integer, String> userNames = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        // 1. Tickets by Status
        Map<String, Integer> ticketsByStatus = new HashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            ticketsByStatus.put(status.name(), 0);
        }
        for (Ticket t : tickets) {
            ticketsByStatus.merge(t.getStatus().name(), 1, Integer::sum);
        }

        // 2. Tickets by Category
        Map<String, Integer> ticketsByCategory = new HashMap<>();
        for (Category c : categories) {
            ticketsByCategory.put(c.getName(), 0);
        }
        for (Ticket t : tickets) {
            String catName = categoryNames.getOrDefault(t.getCategoryId(), "Unknown");
            ticketsByCategory.merge(catName, 1, Integer::sum);
        }

        // 3. Average Resolution Time by Department (in hours)
        Map<String, List<Double>> deptResolutionTimes = new HashMap<>();
        for (Department d : departments) {
            deptResolutionTimes.put(d.getName(), new ArrayList<>());
        }

        for (Ticket t : tickets) {
            if (t.getResolvedAt() != null) {
                Integer deptId = categoryDepts.get(t.getCategoryId());
                if (deptId != null) {
                    String deptName = departmentNames.get(deptId);
                    if (deptName != null) {
                        double hours = Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes() / 60.0;
                        deptResolutionTimes.computeIfAbsent(deptName, k -> new ArrayList<>()).add(hours);
                    }
                }
            }
        }

        Map<String, Double> avgResolutionTimeByDept = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : deptResolutionTimes.entrySet()) {
            List<Double> times = entry.getValue();
            double avg = times.isEmpty() ? 0.0 : times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            avgResolutionTimeByDept.put(entry.getKey(), Math.round(avg * 100.0) / 100.0);
        }

        // 4. Open Tickets by Staff Member
        Map<String, Integer> openTicketsByStaff = new HashMap<>();
        for (Ticket t : tickets) {
            if ((t.getStatus() == TicketStatus.OPEN || t.getStatus() == TicketStatus.IN_PROGRESS || t.getStatus() == TicketStatus.ESCALATED)
                    && t.getAssignedStaffId() != null) {
                String staffName = userNames.getOrDefault(t.getAssignedStaffId(), "Staff#" + t.getAssignedStaffId());
                openTicketsByStaff.merge(staffName, 1, Integer::sum);
            }
        }

        return new AnalyticsReportDto(ticketsByStatus, ticketsByCategory, avgResolutionTimeByDept, openTicketsByStaff, LocalDateTime.now());
    }
}
