package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.outbound.PickingTask;
import com.wms.ai.outbound.TaskStatus;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OutboundServiceImpl.class)
class OutboundServiceCreateTaskTest {

    @Autowired
    WorkerRepository workers;

    @Autowired
    OutboundServiceImpl service;

    @BeforeEach
    void seedWorker() {
        workers.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));
    }

    @Test
    void createTaskReturnsAssignedTaskWithGeneratedId() {
        var task = service.createTask("ORD-1", "WK-1");

        assertThat(task.id()).isNotBlank();
        assertThat(task.orderId()).isEqualTo("ORD-1");
        assertThat(task.workerId()).isEqualTo("WK-1");
        assertThat(task.status()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(task.assignedAt()).isNotNull();
    }

    @Test
    void createdTaskIsPersistedAndReadableViaGetTask() {
        var created = service.createTask("ORD-1", "WK-1");

        var found = service.getTask(created.id());

        assertThat(found).isPresent();
        PickingTask task = found.get();
        assertThat(task.id()).isEqualTo(created.id());
        assertThat(task.orderId()).isEqualTo("ORD-1");
        assertThat(task.workerId()).isEqualTo("WK-1");
        assertThat(task.status()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(task.assignedAt()).isNotNull();
    }

    @Test
    void getTaskIsEmptyForUnknownId() {
        assertThat(service.getTask("NOPE")).isEmpty();
    }

    @Test
    void createTaskAcceptsAnArbitraryOrderId() {
        // orderId is opaque: Outbound has no Order dependency and must not validate it.
        var task = service.createTask("totally-unknown-order", "WK-1");

        assertThat(task.orderId()).isEqualTo("totally-unknown-order");
    }

    @Test
    void createTaskRejectsBlankOrderId() {
        assertThatThrownBy(() -> service.createTask("  ", "WK-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTaskRejectsBlankWorkerId() {
        assertThatThrownBy(() -> service.createTask("ORD-1", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTaskRejectsUnknownWorker() {
        assertThatThrownBy(() -> service.createTask("ORD-1", "WK-UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
