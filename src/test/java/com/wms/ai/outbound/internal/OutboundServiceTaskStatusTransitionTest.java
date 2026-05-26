package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.outbound.TaskStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OutboundServiceImpl.class)
class OutboundServiceTaskStatusTransitionTest {

    private static final Instant ASSIGNED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    PickingTaskRepository repository;

    @Autowired
    OutboundServiceImpl service;

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
        "ASSIGNED,PICKING",
        "ASSIGNED,CANCELLED",
        "PICKING,DONE",
        "PICKING,CANCELLED",
    })
    void legalTransitionUpdatesStatus(TaskStatus from, TaskStatus to) {
        persist("PT-1", from);

        var updated = service.updateTaskStatus("PT-1", to);

        assertThat(updated.status()).isEqualTo(to);
        assertThat(repository.findById("PT-1").orElseThrow().getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
        // skipping forward / going backward
        "ASSIGNED,DONE",
        "PICKING,ASSIGNED",
        // no self-transitions
        "ASSIGNED,ASSIGNED",
        // terminal states cannot move
        "DONE,PICKING",
        "DONE,CANCELLED",
        "CANCELLED,ASSIGNED",
        "CANCELLED,PICKING",
    })
    void illegalTransitionThrowsAndLeavesStatusUnchanged(TaskStatus from, TaskStatus to) {
        persist("PT-1", from);

        assertThatThrownBy(() -> service.updateTaskStatus("PT-1", to))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById("PT-1").orElseThrow().getStatus()).isEqualTo(from);
    }

    @Test
    void updateStatusOfUnknownIdThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.updateTaskStatus("NOPE", TaskStatus.PICKING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void persist(String id, TaskStatus status) {
        repository.saveAndFlush(new PickingTaskEntity(id, "ORD-1", "WK-1", ASSIGNED_AT, status));
    }
}
