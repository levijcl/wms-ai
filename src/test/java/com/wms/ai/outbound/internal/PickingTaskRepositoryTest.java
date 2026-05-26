package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.outbound.TaskStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PickingTaskRepositoryTest {

    private static final Instant ASSIGNED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    PickingTaskRepository repository;

    @Test
    void savesAndFindsTaskWithAllFields() {
        repository.saveAndFlush(
                new PickingTaskEntity("PT-1", "ORD-1", "WK-1", ASSIGNED_AT, TaskStatus.ASSIGNED));

        var found = repository.findById("PT-1");

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("ORD-1");
        assertThat(found.get().getWorkerId()).isEqualTo("WK-1");
        assertThat(found.get().getAssignedAt()).isEqualTo(ASSIGNED_AT);
        assertThat(found.get().getStatus()).isEqualTo(TaskStatus.ASSIGNED);
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertThat(repository.findById("NOPE")).isEmpty();
    }
}
