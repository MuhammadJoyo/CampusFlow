package com.campusflow.service;

import com.campusflow.model.Department;
import com.campusflow.model.Ticket;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Manages departmental ticket queues using thread-safe {@link PriorityBlockingQueue} instances.
 *
 * <h2>Concurrency & Priority Ordering (FR-09, ADR-007, SCD-05)</h2>
 * <ul>
 *   <li>One {@code PriorityBlockingQueue<Ticket>} is maintained per active department.</li>
 *   <li>Tickets implement {@link Comparable} based on Priority urgency (CRITICAL &gt; HIGH &gt;
 *       MEDIUM &gt; LOW), with creation timestamp as tiebreaker (FIFO within same priority).</li>
 *   <li>All operations are thread-safe and non-blocking.</li>
 * </ul>
 */
public class TicketQueueManager {

    private final ConcurrentMap<String, PriorityBlockingQueue<Ticket>> departmentQueues;

    public TicketQueueManager() {
        this.departmentQueues = new ConcurrentHashMap<>();
    }

    /**
     * Initializes department queues for the provided list of departments.
     *
     * @param departments list of active departments
     */
    public void initialise(List<Department> departments) {
        Objects.requireNonNull(departments, "departments list must not be null");
        for (Department dept : departments) {
            String key = normalize(dept.getName());
            departmentQueues.computeIfAbsent(key, k -> new PriorityBlockingQueue<>());
        }
    }

    /**
     * Enqueues a ticket into the target department's priority queue.
     *
     * @param ticket     the ticket to enqueue
     * @param department the target department name
     */
    public void enqueue(Ticket ticket, String department) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(department, "department must not be null");

        PriorityBlockingQueue<Ticket> queue = getOrCreateQueue(department);
        queue.offer(ticket);
    }

    /**
     * Dequeues the highest priority ticket from the specified department queue.
     *
     * @param department department name
     * @return highest priority ticket if available, empty Optional otherwise
     */
    public Optional<Ticket> dequeue(String department) {
        Objects.requireNonNull(department, "department must not be null");
        PriorityBlockingQueue<Ticket> queue = departmentQueues.get(normalize(department));
        if (queue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(queue.poll());
    }

    /**
     * Peeks at the highest priority ticket without removing it.
     *
     * @param department department name
     * @return highest priority ticket if available, empty Optional otherwise
     */
    public Optional<Ticket> peek(String department) {
        Objects.requireNonNull(department, "department must not be null");
        PriorityBlockingQueue<Ticket> queue = departmentQueues.get(normalize(department));
        if (queue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(queue.peek());
    }

    /**
     * Removes a ticket from the specified department queue if present.
     *
     * @param ticketId   ticket ID to remove
     * @param department department name
     * @return true if the ticket was found and removed, false otherwise
     */
    public boolean remove(int ticketId, String department) {
        Objects.requireNonNull(department, "department must not be null");
        PriorityBlockingQueue<Ticket> queue = departmentQueues.get(normalize(department));
        if (queue == null) {
            return false;
        }
        return queue.removeIf(t -> t.getId() == ticketId);
    }

    /**
     * Returns the current number of waiting tickets in the specified department queue.
     *
     * @param department department name
     * @return queue size, or 0 if queue does not exist
     */
    public int size(String department) {
        Objects.requireNonNull(department, "department must not be null");
        PriorityBlockingQueue<Ticket> queue = departmentQueues.get(normalize(department));
        return (queue != null) ? queue.size() : 0;
    }

    /**
     * Clears all tickets across all department queues.
     */
    public void clear() {
        departmentQueues.values().forEach(PriorityBlockingQueue::clear);
    }

    /**
     * Rebuilds the queue for a department with the provided list of active tickets.
     *
     * @param department department name
     * @param tickets    tickets to load
     */
    public void rebuildQueue(String department, List<Ticket> tickets) {
        Objects.requireNonNull(department, "department must not be null");
        PriorityBlockingQueue<Ticket> queue = getOrCreateQueue(department);
        queue.clear();
        if (tickets != null) {
            queue.addAll(tickets);
        }
    }

    private PriorityBlockingQueue<Ticket> getOrCreateQueue(String department) {
        return departmentQueues.computeIfAbsent(normalize(department), k -> new PriorityBlockingQueue<>());
    }

    private String normalize(String department) {
        return department.trim().toUpperCase();
    }
}
