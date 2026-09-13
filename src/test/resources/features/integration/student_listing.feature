# Second pedagogical example: unlike user_registration.feature (UserService called directly, with
# a mocked repository), this scenario goes through the real HTTP route via MockMvc, backed by a
# real MySQL Testcontainer — the same style as StudentControllerTest, expressed in Gherkin.
#
# @integration, not @unit: it needs a full Spring context and Docker (see CucumberSpringConfiguration),
# so it's excluded from the pre-commit hook's -Dgroups=unit filter and only runs on a full "mvn test".
@integration
Feature: Student listing via the HTTP API

  As an authenticated librarian, I want to list the students stored in the system through the API.

  Scenario: An authenticated librarian can list existing students
    Given a librarian is registered and logged in
    And a student "Ada Lovelace" has been created
    When I call GET "/api/students" with the librarian's token
    Then the response contains a student named "Ada Lovelace"
