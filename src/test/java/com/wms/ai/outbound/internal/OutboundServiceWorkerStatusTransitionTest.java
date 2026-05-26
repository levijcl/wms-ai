package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OutboundServiceImpl.class)
class OutboundServiceWorkerStatusTransitionTest {

    @Autowired
    WorkerRepository repository;

    @Autowired
    OutboundServiceImpl service;

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
        "IDLE,BUSY",
        "IDLE,OFFLINE",
        "BUSY,IDLE",
        "BUSY,OFFLINE",
        "OFFLINE,IDLE",
    })
    void legalTransitionUpdatesStatus(WorkerStatus from, WorkerStatus to) {
        persist("WK-1", from);

        var updated = service.updateWorkerStatus("WK-1", to);

        assertThat(updated.status()).isEqualTo(to);
        assertThat(repository.findById("WK-1").orElseThrow().getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
        // no self-transitions
        "IDLE,IDLE",
        "BUSY,BUSY",
        "OFFLINE,OFFLINE",
        // an OFFLINE worker must come back to IDLE before being made BUSY
        "OFFLINE,BUSY",
    })
    void illegalTransitionThrowsAndLeavesStatusUnchanged(WorkerStatus from, WorkerStatus to) {
        persist("WK-1", from);

        assertThatThrownBy(() -> service.updateWorkerStatus("WK-1", to))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById("WK-1").orElseThrow().getStatus()).isEqualTo(from);
    }

    @Test
    void assigningAnAlreadyBusyWorkerIsRejected() {
        // README §6: a worker that raced to BUSY can no longer be assigned.
        persist("WK-1", WorkerStatus.BUSY);

        assertThatThrownBy(() -> service.updateWorkerStatus("WK-1", WorkerStatus.BUSY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateStatusOfUnknownIdThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.updateWorkerStatus("NOPE", WorkerStatus.BUSY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void persist(String id, WorkerStatus status) {
        repository.saveAndFlush(new WorkerEntity(id, "Worker " + id, "ZONE-1", status));
    }
}
