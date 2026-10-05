package com.scoregrid.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Eureka off: a local run must not register a test gateway in a running stack.
@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
