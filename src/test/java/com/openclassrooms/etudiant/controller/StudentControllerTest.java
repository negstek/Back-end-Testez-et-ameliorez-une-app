package com.openclassrooms.etudiant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclassrooms.etudiant.dto.LoginRequestDTO;
import com.openclassrooms.etudiant.dto.StudentRequestDTO;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.StudentRepository;
import com.openclassrooms.etudiant.repository.UserRepository;
import com.openclassrooms.etudiant.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

// "integration": full Spring context + real MySQL via Testcontainers — needs Docker, excluded from the pre-commit hook
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public class StudentControllerTest {

    private static final String URL = "/api/students";
    private static final String LOGIN = "librarian";
    private static final String PASSWORD = "password";

    @Container
    static MySQLContainer mySQLContainer = new MySQLContainer("mysql:latest");

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;

    private String token;

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mySQLContainer.getJdbcUrl());
        registry.add("spring.datasource.username", () -> mySQLContainer.getUsername());
        registry.add("spring.datasource.password", () -> mySQLContainer.getPassword());
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        User user = new User();
        user.setFirstName("Jane");
        user.setLastName("Librarian");
        user.setLogin(LOGIN);
        user.setPassword(PASSWORD);
        userService.register(user);

        LoginRequestDTO loginRequestDTO = new LoginRequestDTO();
        loginRequestDTO.setLogin(LOGIN);
        loginRequestDTO.setPassword(PASSWORD);

        // Real login round-trip, not a hand-built token: exercises the same path a real client
        // uses, including JwtService + JwtAuthenticationFilter, instead of bypassing them
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .content(objectMapper.writeValueAsString(loginRequestDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(response).get("token").asText();
    }

    @AfterEach
    public void afterEach() {
        // Both tables are cleared after every test so each test starts from an empty, predictable
        // state — several tests below (e.g. findAllReturnsExistingStudents) assert on array index 0,
        // which only holds if no student survives from a previous test.
        studentRepository.deleteAll();
        userRepository.deleteAll();
    }

    private StudentRequestDTO buildValidStudentRequest() {
        StudentRequestDTO studentRequestDTO = new StudentRequestDTO();
        studentRequestDTO.setFirstName("Ada");
        studentRequestDTO.setLastName("Lovelace");
        studentRequestDTO.setEmail("ada@mail.com");
        studentRequestDTO.setBirthDate(LocalDate.of(1815, 12, 10));
        return studentRequestDTO;
    }

    private Student saveStudent() {
        return studentRepository.save(Student.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@mail.com")
                .birthDate(LocalDate.of(1815, 12, 10))
                .build());
    }

    // Verifies the nominal creation path: valid token + valid payload returns the created student
    @Test
    public void createWithValidTokenAndDataReturnsCreated() throws Exception {
        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("ada@mail.com"));
    }

    // Verifies that every /api/students route requires authentication
    @Test
    public void createWithoutTokenReturnsUnauthorized() throws Exception {
        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN: this 401 comes from Spring Security's authenticationEntryPoint
                // (response.sendError in SpringSecurityConfig), not from RestExceptionHandler —
                // that's why, unlike the 400/404 cases below, there is no JSON error body to assert on
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    // Verifies that bean validation (@NotBlank/@Email) rejects an invalid payload before it reaches the service
    @Test
    public void createWithInvalidDataReturnsBadRequest() throws Exception {
        // GIVEN
        StudentRequestDTO invalid = buildValidStudentRequest();
        invalid.setEmail("");

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(invalid))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // Verifies that the service's email-uniqueness check is enforced through the full HTTP stack
    @Test
    public void createWithAlreadyUsedEmailReturnsBadRequest() throws Exception {
        // GIVEN: first creation succeeds and occupies the email
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        // WHEN: second creation reuses the same email
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // Verifies that GET /api/students lists every student currently persisted
    @Test
    public void findAllReturnsExistingStudents() throws Exception {
        // GIVEN
        Student student = saveStudent();

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN: index 0 is safe only because afterEach() guarantees this is the only student
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(student.getId()));
    }

    // Verifies that GET /api/students/{id} returns the student matching that id
    @Test
    public void findByIdReturnsTheMatchingStudent() throws Exception {
        // GIVEN
        Student student = saveStudent();

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.get(URL + "/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("ada@mail.com"));
    }


    // Verifies that PUT /api/students/{id} persists the new field values
    @Test
    public void updateAppliesNewFields() throws Exception {
        // GIVEN
        Student student = saveStudent();
        StudentRequestDTO updated = buildValidStudentRequest();
        updated.setFirstName("Grace");
        updated.setEmail("grace@mail.com");

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.put(URL + "/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(updated))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Grace"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value("grace@mail.com"));
    }

    // Verifies that DELETE /api/students/{id} succeeds with 204 and no body
    @Test
    public void deleteRemovesTheStudent() throws Exception {
        // GIVEN
        Student student = saveStudent();

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.delete(URL + "/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    // One JSON body shared by every parameterized case below — PUT needs a *valid* payload here,
    // since @Valid runs before the controller method body: an invalid body would 400 before the
    // unknown id is ever checked, masking the 404 this test is actually about.
    private static Stream<Arguments> requestsOnAnUnknownId() {
        return Stream.of(
                Arguments.of("GET", (Function<String, MockHttpServletRequestBuilder>) MockMvcRequestBuilders::get),
                Arguments.of("PUT", (Function<String, MockHttpServletRequestBuilder>) url ->
                        MockMvcRequestBuilders.put(url)
                                .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"ada@mail.com\",\"birthDate\":\"1815-12-10\"}")
                                .contentType(MediaType.APPLICATION_JSON)),
                Arguments.of("DELETE", (Function<String, MockHttpServletRequestBuilder>) MockMvcRequestBuilders::delete)
        );
    }

    // Verifies that GET/PUT/DELETE on an unknown id all 404 the same way, mapped from
    // StudentNotFoundException by RestExceptionHandler — one parameterized test replaces what would
    // otherwise be three near-identical tests differing only in HTTP method
    @ParameterizedTest(name = "{0} on an unknown id returns 404")
    @MethodSource("requestsOnAnUnknownId")
    public void unknownIdReturnsNotFound(String httpMethod, Function<String, MockHttpServletRequestBuilder> requestBuilder) throws Exception {
        // WHEN
        mockMvc.perform(requestBuilder.apply(URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
