# Pedagogical example of Cucumber/Gherkin usage. These 3 scenarios re-express, in business-readable
# language, the same UserService rules already covered by UserServiceTest — the point here is to
# illustrate the BDD style itself (Given/When/Then mapped to step definitions in UserRegistrationSteps),
# not to add new coverage.
#
# @unit here is documentation, not what keeps this fast in the pre-commit hook: what actually does
# is that RunCucumberTest's glue is scoped to this package only (see cucumber/unit/RunCucumberTest.java)
# — kept separate from the Spring-backed cucumber/integration package so cucumber-spring's context
# (and Testcontainers/Docker) is never even discovered when only this suite runs. The tag still
# reflects reality accurately, like the other @Tag("unit") classes: mocks only, no Spring, no Docker.
@unit
Feature: User registration and login

  As a visitor of the application, I want to register an account and then log in with it,
  so that I can access the features reserved to authenticated users.

  Scenario: Registering with a free login succeeds and the password is encoded
    Given no user is registered with the login "alice"
    When I register a new user with login "alice" and password "s3cret!"
    Then the registration succeeds
    And the stored password is not the plain text "s3cret!"

  Scenario: Registering with a login that is already taken is rejected
    Given a user is already registered with the login "alice"
    When I register a new user with login "alice" and password "s3cret!"
    Then the registration is rejected

  Scenario: Logging in with the wrong password is rejected
    Given a user is registered with the login "alice" and password "correct-password"
    When I log in with login "alice" and password "wrong-password"
    Then the login is rejected
