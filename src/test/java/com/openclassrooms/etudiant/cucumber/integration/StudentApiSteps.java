package com.openclassrooms.etudiant.cucumber.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclassrooms.etudiant.dto.LoginRequestDTO;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.StudentRepository;
import com.openclassrooms.etudiant.repository.UserRepository;
import com.openclassrooms.etudiant.service.UserService;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Step definitions for student_listing.feature. Unlike UserRegistrationSteps (service called
// directly, repository mocked), these go through the real HTTP route via MockMvc, against the
// Spring context + Testcontainers MySQL wired by CucumberSpringConfiguration — the fields below
// are @Autowired rather than built by hand, since cucumber-spring injects this class from that
// shared context instead of leaving it to construct its own collaborators.
public class StudentApiSteps {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;

    private String token;
    private String responseBody;

    // The Spring context (and its database) is shared and cached across scenarios for speed, so
    // each scenario must clean up after itself rather than relying on a fresh context every time —
    // this @After hook plays the same role AfterEach.afterEach() plays in StudentControllerTest.
    @After
    public void cleanUpDatabase() {
        studentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Given("a librarian is registered and logged in")
    public void a_librarian_is_registered_and_logged_in() throws Exception {
        User user = new User();
        user.setFirstName("Jane");
        user.setLastName("Librarian");
        user.setLogin("librarian");
        user.setPassword("password");
        userService.register(user);

        LoginRequestDTO loginRequestDTO = new LoginRequestDTO();
        loginRequestDTO.setLogin("librarian");
        loginRequestDTO.setPassword("password");

        // Real login round-trip (not a hand-built token), exactly like StudentControllerTest, so
        // this scenario also exercises JwtService + JwtAuthenticationFilter, not just the listing
        // endpoint on its own.
        String loginResponse = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .content(objectMapper.writeValueAsString(loginRequestDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(loginResponse).get("token").asText();
    }

    @Given("a student {string} has been created")
    public void a_student_has_been_created(String fullName) {
        String[] nameParts = fullName.split(" ", 2);
        studentRepository.save(Student.builder()
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .email(nameParts[0].toLowerCase() + "@mail.com")
                .birthDate(LocalDate.of(1815, 12, 10))
                .build());
    }

    @When("I call GET {string} with the librarian's token")
    public void i_call_get_with_the_librarians_token(String url) throws Exception {
        responseBody = mockMvc.perform(MockMvcRequestBuilders.get(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
    }

    @Then("the response contains a student named {string}")
    public void the_response_contains_a_student_named(String fullName) throws Exception {
        String[] nameParts = fullName.split(" ", 2);
        JsonNode students = objectMapper.readTree(responseBody);
        boolean found = false;
        for (JsonNode student : students) {
            if (student.get("firstName").asText().equals(nameParts[0])
                    && student.get("lastName").asText().equals(nameParts[1])) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }
}
