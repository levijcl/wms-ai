package com.wms.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling turns on Spring's scheduler so the dev-only FloorSimulator's @Scheduled
// tick can run. It is harmless when no scheduled bean is active (the simulator is gated off
// by default), so it has no effect on tests or a production run that leaves it disabled.
@SpringBootApplication
@EnableScheduling
public class AiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiApplication.class, args);
	}

}
