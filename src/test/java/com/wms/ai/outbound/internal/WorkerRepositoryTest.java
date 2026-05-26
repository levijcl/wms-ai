package com.wms.ai.outbound.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.ai.outbound.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class WorkerRepositoryTest {

    @Autowired
    WorkerRepository repository;

    @Test
    void savesAndFindsWorkerWithAllFields() {
        repository.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));

        var found = repository.findById("WK-1");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
        assertThat(found.get().getCurrentZone()).isEqualTo("ZONE-1");
        assertThat(found.get().getStatus()).isEqualTo(WorkerStatus.IDLE);
    }

    @Test
    void findByStatusReturnsOnlyMatchingWorkers() {
        repository.saveAndFlush(new WorkerEntity("WK-1", "Alice", "ZONE-1", WorkerStatus.IDLE));
        repository.saveAndFlush(new WorkerEntity("WK-2", "Bob", "ZONE-2", WorkerStatus.BUSY));
        repository.saveAndFlush(new WorkerEntity("WK-3", "Carol", "ZONE-1", WorkerStatus.IDLE));

        assertThat(repository.findByStatus(WorkerStatus.IDLE))
                .extracting(WorkerEntity::getId)
                .containsExactlyInAnyOrder("WK-1", "WK-3");
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertThat(repository.findById("NOPE")).isEmpty();
    }
}
