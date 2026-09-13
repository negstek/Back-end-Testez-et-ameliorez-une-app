package com.openclassrooms.etudiant.cucumber.integration;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

// Entry point for the Spring-backed Cucumber scenarios (StudentApiSteps + CucumberSpringConfiguration):
// real MockMvc, real MySQL via Testcontainers, needs Docker. Deliberately a separate Suite/glue
// package from RunCucumberTest (the plain-Mockito one) rather than one Suite tag-filtering
// scenarios in and out: cucumber-spring starts its Spring context (and this class's Testcontainer)
// once per scenario *run through this glue*, regardless of that particular scenario's own tags —
// so if this step definition class shared a glue package with a "unit" one, Docker would start
// even for a run where only the untagged-for-Spring scenarios survive filtering. Splitting the glue
// packages avoids that entirely: this suite (and its Testcontainer) is simply never discovered when
// only RunCucumberTest is selected.
//
// Excluded from the pre-commit hook by name (-Dtest='!RunCucumberIntegrationTest', see
// .githooks/pre-commit) rather than by @Tag on this class: a Java @Tag on a @Suite class doesn't
// reliably prune it from a tag-filtered run — see RunCucumberTest's history in git log for how
// that was found out the hard way. A plain "mvn test" still runs it.
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/integration")
@ConfigurationParameter(key = "cucumber.glue", value = "com.openclassrooms.etudiant.cucumber.integration")
public class RunCucumberIntegrationTest {
}
