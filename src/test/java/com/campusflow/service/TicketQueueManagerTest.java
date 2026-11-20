package com.campusflow.service;

import com.campusflow.model.Department;
import com.campusflow.model.Ticket;
import com.campusflow.model.enums.Priority;
import com.campusflow.model.enums.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TicketQueueManager")
class TicketQueueManagerTest {

    private TicketQueueManager queueManager;

    @BeforeEach
    void setUp() {
        queueManager = new TicketQueueManager();
        List<Department> departments = List.of(
                new Department(1, "IT", "Information Tech"),
                new Department(2, "Maintenance", "Facilities"),
                new Department(3, "Academic", "Academic services")
        );
        queueManager.initialise(departments);
    }

    @Test
    @DisplayName("Tickets in queue are ordered by priority urgency: CRITICAL > HIGH > MEDIUM > LOW")
    void priorityOrdering() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 20, 10, 0);

        Ticket low = new Ticket.Builder().id(1).title("T1").description("D").priority(Priority.LOW).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(base).build();
        Ticket critical = new Ticket.Builder().id(2).title("T2").description("D").priority(Priority.CRITICAL).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(base.plusMinutes(5)).build();
        Ticket high = new Ticket.Builder().id(3).title("T3").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(base.plusMinutes(10)).build();
        Ticket medium = new Ticket.Builder().id(4).title("T4").description("D").priority(Priority.MEDIUM).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(base.plusMinutes(15)).build();

        // Enqueue in arbitrary order
        queueManager.enqueue(low, "IT");
        queueManager.enqueue(high, "IT");
        queueManager.enqueue(critical, "IT");
        queueManager.enqueue(medium, "IT");

        assertEquals(4, queueManager.size("IT"));

        // Dequeue must follow priority urgency
        assertEquals(2, queueManager.dequeue("IT").orElseThrow().getId(), "CRITICAL must be first");
        assertEquals(3, queueManager.dequeue("IT").orElseThrow().getId(), "HIGH must be second");
        assertEquals(4, queueManager.dequeue("IT").orElseThrow().getId(), "MEDIUM must be third");
        assertEquals(1, queueManager.dequeue("IT").orElseThrow().getId(), "LOW must be fourth");
        assertTrue(queueManager.dequeue("IT").isEmpty(), "Queue must be empty after all dequeued");
    }

    @Test
    @DisplayName("Tickets with equal priority are ordered FIFO by creation timestamp")
    void equalPriorityTiebreakerFifo() {
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 20, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 20, 9, 30);
        LocalDateTime t3 = LocalDateTime.of(2026, 11, 20, 10, 0);

        Ticket ticketEarly = new Ticket.Builder().id(10).title("Early").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(t1).build();
        Ticket ticketMid = new Ticket.Builder().id(20).title("Mid").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(t2).build();
        Ticket ticketLate = new Ticket.Builder().id(30).title("Late").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(t3).build();

        // Enqueue out of order
        queueManager.enqueue(ticketLate, "Maintenance");
        queueManager.enqueue(ticketEarly, "Maintenance");
        queueManager.enqueue(ticketMid, "Maintenance");

        assertEquals(10, queueManager.dequeue("Maintenance").orElseThrow().getId(), "Earlier ticket must dequeue first");
        assertEquals(20, queueManager.dequeue("Maintenance").orElseThrow().getId());
        assertEquals(30, queueManager.dequeue("Maintenance").orElseThrow().getId());
    }

    @Test
    @DisplayName("remove eliminates the ticket from the queue")
    void removeTicket() {
        Ticket t1 = new Ticket.Builder().id(101).title("T1").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(LocalDateTime.now()).build();
        Ticket t2 = new Ticket.Builder().id(102).title("T2").description("D").priority(Priority.HIGH).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(LocalDateTime.now()).build();

        queueManager.enqueue(t1, "IT");
        queueManager.enqueue(t2, "IT");

        assertTrue(queueManager.remove(101, "IT"));
        assertEquals(1, queueManager.size("IT"));
        assertEquals(102, queueManager.peek("IT").orElseThrow().getId());
    }

    @Test
    @DisplayName("rebuildQueue replaces queue contents cleanly")
    void rebuildQueue() {
        Ticket t1 = new Ticket.Builder().id(1).title("T1").description("D").priority(Priority.LOW).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(LocalDateTime.now()).build();
        queueManager.enqueue(t1, "Academic");

        Ticket t2 = new Ticket.Builder().id(2).title("T2").description("D").priority(Priority.CRITICAL).status(TicketStatus.OPEN).categoryId(1).submitterId(1).createdAt(LocalDateTime.now()).build();
        queueManager.rebuildQueue("Academic", List.of(t2));

        assertEquals(1, queueManager.size("Academic"));
        assertEquals(2, queueManager.dequeue("Academic").orElseThrow().getId());
    }

    @Test
    @DisplayName("Concurrent multi-threaded dequeue guarantees zero duplicate claims and zero lost tickets")
    void concurrentDequeueSafety() throws InterruptedException {
        int ticketCount = 50;
        int threadCount = 10;

        for (int i = 1; i <= ticketCount; i++) {
            Ticket t = new Ticket.Builder()
                    .id(i)
                    .title("Ticket #" + i)
                    .description("D")
                    .priority(Priority.MEDIUM)
                    .status(TicketStatus.OPEN)
                    .categoryId(1)
                    .submitterId(1)
                    .createdAt(LocalDateTime.now().plusNanos(i * 1000L))
                    .build();
            queueManager.enqueue(t, "IT");
        }

        assertEquals(ticketCount, queueManager.size("IT"));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> claimedIds = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger nullCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    while (true) {
                        Optional<Ticket> opt = queueManager.dequeue("IT");
                        if (opt.isPresent()) {
                            claimedIds.add(opt.get().getId());
                        } else {
                            nullCount.incrementAndGet();
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(ticketCount, claimedIds.size(), "Every ticket must be dequeued exactly once");
        assertEquals(0, queueManager.size("IT"), "Queue must be empty");

        // Verify all IDs from 1 to 50 are present with no duplicates
        List<Integer> sortedClaims = new ArrayList<>(claimedIds);
        Collections.sort(sortedClaims);
        for (int i = 1; i <= ticketCount; i++) {
            assertEquals(i, sortedClaims.get(i - 1));
        }
    }
}
