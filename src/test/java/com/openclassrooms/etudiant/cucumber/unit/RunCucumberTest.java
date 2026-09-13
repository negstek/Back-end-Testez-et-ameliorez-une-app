package com.openclassrooms.etudiant.cucumber.unit;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

// Entry point that lets Surefire's JUnit Platform provider run the pure-Mockito Cucumber scenarios
// (UserRegistrationSteps): @Suite delegates discovery to the "cucumber" engine instead of running
// @Test methods directly, so this class itself declares none. Named "...Test" to match Surefire's
// default include pattern (**/*Test.java) with no extra pom.xml configuration.
//
// Scoped to the "cucumber.unit" glue package and "features/unit" resources specifically — kept
// separate from RunCucumberIntegrationTest (see that class for why): mixing a Spring-backed step
// definition class into the same glue package as this one would make cucumber-spring start a real
// Spring context (and Testcontainers/Docker) for every scenario run through this suite, including
// these plain-Mockito ones, regardless of any tag filtering.
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/unit")
@ConfigurationParameter(key = "cucumber.glue", value = "com.openclassrooms.etudiant.cucumber.unit")
public class RunCucumberTest {
}
