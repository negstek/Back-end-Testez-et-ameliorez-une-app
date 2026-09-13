package com.openclassrooms.etudiant.cucumber.unit;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

// Once cucumber-spring is a dependency anywhere in the project (it is, for the integration glue's
// StudentApiSteps), it becomes the ObjectFactory for every Cucumber run on the classpath, and it
// requires exactly one @CucumberContextConfiguration class reachable from whichever glue package is
// running — even this one, where UserRegistrationSteps never uses @Autowired at all (it builds its
// own UserService and mocks by hand). A plain @ContextConfiguration (not @SpringBootTest) with an
// empty @Configuration class satisfies that requirement with a trivial, instantly-created Spring
// context — no auto-configuration, no database, no Docker — so this suite stays fast for the
// pre-commit hook.
@CucumberContextConfiguration
@ContextConfiguration(classes = CucumberUnitConfiguration.EmptyConfig.class)
public class CucumberUnitConfiguration {

    @Configuration
    static class EmptyConfig {
    }
}
