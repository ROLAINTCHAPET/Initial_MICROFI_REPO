package com.microfi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The JWT secret has no default any more (JwtService#validateSecretKey refuses a blank one), so
// the context needs one supplied here rather than inherited from application.properties.
@SpringBootTest(properties =
        "application.security.jwt.secret-key=test-only-secret-not-used-outside-this-context-0123456789")
class MicrofiCoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
