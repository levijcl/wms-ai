package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.outbound.Worker;
import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(OutboundServiceImpl.class)
class OutboundServiceWorkerReadTest {

    @Autowired
    WorkerRepository repository;

    @Autowired
    OutboundServiceImpl service;

    @Test
    void getWorkerReturnsWorkerForKnownId() {
        repository.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));

        var found = service.getWorker("WK-1");

        assertThat(found).isPresent();
        Worker worker = found.get();
        assertThat(worker.id()).isEqualTo("WK-1");
        assertThat(worker.name()).isEqualTo("Alice");
        assertThat(worker.currentZone()).isEqualTo("ZONE-1");
        assertThat(worker.status()).isEqualTo(WorkerStatus.IDLE);
    }

    @Test
    void getWorkerIsEmptyForUnknownId() {
        assertThat(service.getWorker("NOPE")).isEmpty();
    }

    @Test
    void listWorkersByStatusReturnsOnlyMatching() {
        repository.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));
        repository.saveAndFlush(new WorkerEntity("WK-2", "Bob", "ZONE-2", WorkerStatus.BUSY));
        repository.saveAndFlush(new WorkerEntity("WK-3", "Carol", "ZONE-1", WorkerStatus.IDLE));
        repository.saveAndFlush(new WorkerEntity("WK-4", "Dave", "ZONE-3", WorkerStatus.OFFLINE));

        assertThat(service.listWorkersByStatus(WorkerStatus.IDLE))
                .extracting(Worker::id)
                .containsExactlyInAnyOrder("WK-1", "WK-3");
        assertThat(service.listWorkersByStatus(WorkerStatus.BUSY))
                .extracting(Worker::id)
                .containsExactly("WK-2");
    }

    @Test
    void listWorkersByStatusIsEmptyWhenNoneMatch() {
        repository.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));

        assertThat(service.listWorkersByStatus(WorkerStatus.OFFLINE)).isEmpty();
    }
}
