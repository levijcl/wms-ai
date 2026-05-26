package com.wms.ai.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Task 1 skeleton check: the component scan picks up the package-private
 * {@code DispatchServiceImpl} and wires it with the three business-module ports, so
 * the {@link DispatchService} port is injectable. The impl's behaviour is filled in
 * by later tasks; here we only assert the seam exists and the context loads.
 */
@SpringBootTest
class DispatchServiceContextTest {

    @Autowired
    DispatchService dispatchService;

    @Test
    void coordinatorBeanIsWiredAndExposedAsThePort() {
        assertThat(dispatchService).isNotNull();
    }
}
