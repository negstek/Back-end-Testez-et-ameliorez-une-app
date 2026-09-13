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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

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

        // Real login round-trip, not a hand-built token: exercises the same path a real client uses
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .content(objectMapper.writeValueAsString(loginRequestDTO))
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(response).get("token").asText();
    }

    @AfterEach
    public void afterEach() {
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

    @Test
    public void createWithoutTokenReturnsUnauthorized() throws Exception {
        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

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

    @Test
    public void createWithAlreadyUsedEmailReturnsBadRequest() throws Exception {
        // GIVEN
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    public void findAllReturnsExistingStudents() throws Exception {
        // GIVEN
        Student student = saveStudent();

        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(student.getId()));
    }

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

    @Test
    public void findByIdWithUnknownIdReturnsNotFound() throws Exception {
        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.get(URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

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

    @Test
    public void updateWithUnknownIdReturnsNotFound() throws Exception {
        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.put(URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .content(objectMapper.writeValueAsString(buildValidStudentRequest()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

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

    @Test
    public void deleteWithUnknownIdReturnsNotFound() throws Exception {
        // WHEN
        mockMvc.perform(MockMvcRequestBuilders.delete(URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andDo(print())
                // THEN
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
