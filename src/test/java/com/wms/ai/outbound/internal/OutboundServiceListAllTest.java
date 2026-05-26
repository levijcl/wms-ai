package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Covers the whole-pool reads added for the coordinator's {@code warehouseState()}
 * snapshot: {@code listWorkers()} and {@code listTasks()} return every row regardless
 * of status, mapped to the public records.
 */
@DataJpaTest
@Import(OutboundServiceImpl.class)
class OutboundServiceListAllTest {

    @Autowired
    WorkerRepository workers;

    @Autowired
    OutboundServiceImpl service;

    @Test
    void listWorkersReturnsEveryWorkerAcrossAllStatuses() {
        workers.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));
        workers.saveAndFlush(new WorkerEntity("WK-2", "Bob", "ZONE-2", WorkerStatus.BUSY));
        workers.saveAndFlush(new WorkerEntity("WK-3", "Carol", "ZONE-3", WorkerStatus.OFFLINE));

        assertThat(service.listWorkers())
                .extracting(Worker::id)
                .containsExactlyInAnyOrder("WK-1", "WK-2", "WK-3");
    }

    @Test
    void listWorkersIsEmptyWhenNoneExist() {
        assertThat(service.listWorkers()).isEmpty();
    }

    @Test
    void listTasksReturnsEveryTaskCreated() {
        workers.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));
        var first = service.createTask("ORD-1", "WK-1");
        var second = service.createTask("ORD-2", "WK-1");

        assertThat(service.listTasks())
                .extracting(PickingTask::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void listTasksIsEmptyBeforeAnyTaskIsCreated() {
        assertThat(service.listTasks()).isEmpty();
    }
}
