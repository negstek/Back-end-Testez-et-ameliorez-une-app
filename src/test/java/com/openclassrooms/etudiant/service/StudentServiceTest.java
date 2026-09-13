package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.exception.StudentNotFoundException;
import com.openclassrooms.etudiant.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// "unit": no Spring context, no Docker — safe and fast to run in the pre-commit hook
@Tag("unit")
@ExtendWith(SpringExtension.class)
public class StudentServiceTest {

    private static final Long ID = 1L;
    private static final String FIRST_NAME = "Ada";
    private static final String LAST_NAME = "Lovelace";
    private static final String EMAIL = "ada@mail.com";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1815, 12, 10);

    @Mock
    private StudentRepository studentRepository;
    @InjectMocks
    private StudentService studentService;

    private Student buildStudent() {
        return Student.builder()
                .id(ID)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .email(EMAIL)
                .birthDate(BIRTH_DATE)
                .build();
    }

    // Verifies the nominal path: a free email lets the student be persisted and returned
    @Test
    public void create_savesAndReturnsTheStudentWhenEmailIsFree() {
        // GIVEN
        Student student = buildStudent();
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(studentRepository.save(student)).thenReturn(student);

        // WHEN
        Student result = studentService.create(student);

        // THEN
        assertThat(result).isEqualTo(student);
    }

    // Verifies that creation is rejected when another student already uses the same email
    @Test
    public void create_throwsWhenEmailIsAlreadyUsed() {
        // GIVEN
        Student student = buildStudent();
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildStudent()));

        // WHEN / THEN
        assertThatThrownBy(() -> studentService.create(student))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Verifies that findAll() is a pure pass-through of whatever the repository returns
    @Test
    public void findAll_returnsEveryStudentFromTheRepository() {
        // GIVEN
        List<Student> students = List.of(buildStudent(), buildStudent());
        when(studentRepository.findAll()).thenReturn(students);

        // WHEN
        List<Student> result = studentService.findAll();

        // THEN
        assertThat(result).isEqualTo(students);
    }

    // Verifies that findById() returns the student when the repository finds one
    @Test
    public void findById_returnsTheMatchingStudent() {
        // GIVEN
        Student student = buildStudent();
        when(studentRepository.findById(ID)).thenReturn(Optional.of(student));

        // WHEN
        Student result = studentService.findById(ID);

        // THEN
        assertThat(result).isEqualTo(student);
    }

    // Verifies that findById() surfaces a domain-specific exception, not an empty result, for an unknown id
    @Test
    public void findById_throwsStudentNotFoundExceptionWhenIdIsUnknown() {
        // GIVEN
        when(studentRepository.findById(ID)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> studentService.findById(ID))
                .isInstanceOf(StudentNotFoundException.class);
    }

    // Verifies that update() overwrites every mutable field while keeping the original id
    @Test
    public void update_appliesNewFieldsAndKeepsTheSameId() {
        // GIVEN
        Student existingStudent = buildStudent();
        Student updatedFields = Student.builder()
                .firstName("Grace")
                .lastName("Hopper")
                .email("grace@mail.com")
                .birthDate(LocalDate.of(1906, 12, 9))
                .build();
        when(studentRepository.findById(ID)).thenReturn(Optional.of(existingStudent));
        when(studentRepository.findByEmail("grace@mail.com")).thenReturn(Optional.empty());
        // thenAnswer + getArgument(0): echoes back whatever save() is called with, since update()
        // mutates and returns existingStudent itself — a fixed thenReturn(...) couldn't reflect that
        when(studentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        Student result = studentService.update(ID, updatedFields);

        // THEN
        assertThat(result.getId()).isEqualTo(ID);
        assertThat(result.getFirstName()).isEqualTo("Grace");
        assertThat(result.getLastName()).isEqualTo("Hopper");
        assertThat(result.getEmail()).isEqualTo("grace@mail.com");
        assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1906, 12, 9));
    }

    // Verifies that a student keeping its own unchanged email is not rejected by the uniqueness check
    @Test
    public void update_keepingItsOwnUnchangedEmailDoesNotTriggerTheUniquenessCheck() {
        // GIVEN: findByEmail resolves to the student being updated itself (id = ID)
        Student existingStudent = buildStudent();
        when(studentRepository.findById(ID)).thenReturn(Optional.of(existingStudent));
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingStudent));
        when(studentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        Student result = studentService.update(ID, buildStudent());

        // THEN
        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }

    // Verifies that the uniqueness check rejects an email that belongs to a *different* student
    @Test
    public void update_throwsWhenEmailBelongsToAnotherStudent() {
        // GIVEN
        Student existingStudent = buildStudent();
        Student otherStudent = Student.builder().id(2L).email(EMAIL).build();
        Student updatedFields = Student.builder().email(EMAIL).build();
        when(studentRepository.findById(ID)).thenReturn(Optional.of(existingStudent));
        when(studentRepository.findByEmail(EMAIL)).thenReturn(Optional.of(otherStudent));

        // WHEN / THEN
        assertThatThrownBy(() -> studentService.update(ID, updatedFields))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Verifies that update() fails the same way findById() does for an unknown id
    @Test
    public void update_throwsStudentNotFoundExceptionWhenIdIsUnknown() {
        // GIVEN
        when(studentRepository.findById(ID)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> studentService.update(ID, buildStudent()))
                .isInstanceOf(StudentNotFoundException.class);
    }

    // Verifies that delete() removes an existing student (void method: the repository call is the only observable effect)
    @Test
    public void delete_removesTheStudentWhenIdExists() {
        // GIVEN
        Student student = buildStudent();
        when(studentRepository.findById(ID)).thenReturn(Optional.of(student));

        // WHEN
        studentService.delete(ID);

        // THEN
        verify(studentRepository).delete(student);
    }

    // Verifies that delete() does not attempt to remove anything when the id doesn't exist
    @Test
    public void delete_throwsStudentNotFoundExceptionWhenIdIsUnknown() {
        // GIVEN
        when(studentRepository.findById(ID)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> studentService.delete(ID))
                .isInstanceOf(StudentNotFoundException.class);
        // never(): proves the exception comes from findById() short-circuiting delete() entirely,
        // not from some later failure inside the repository call
        verify(studentRepository, never()).delete(any());
    }
}
