package com.openclassrooms.etudiant.cucumber.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

import io.cucumber.spring.CucumberContextConfiguration;

// Bootstraps the one Spring context (real MySQL via Testcontainers, same setup as
// StudentControllerTest) that every Cucumber step definition class in this package shares.
// @CucumberContextConfiguration marks this class as cucumber-spring's entry point: Cucumber still
// creates a fresh StudentApiSteps instance per scenario, but cucumber-spring wires each of those
// instances to this same cached ApplicationContext instead of starting one per scenario.
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {

    // @Testcontainers/@Container won't start this automatically: that JUnit 5 extension only
    // starts containers for classes the Jupiter engine runs directly, and this class never is —
    // cucumber-spring only reads it for configuration. Constructing the object here is harmless
    // (it doesn't touch Docker), but start() must be called by hand — and specifically from inside
    // configureTestProperties() below, not from a static initializer: a static block would run the
    // instant this class is loaded, i.e. as soon as anything scans this glue package, rather than
    // only when Spring genuinely prepares this ApplicationContext for a scenario that's about to
    // run. start() itself is idempotent, in case Spring calls this more than once for the same
    // cached context.
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:latest");

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        mySQLContainer.start();
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }
}
