package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.exception.StudentNotFoundException;
import com.openclassrooms.etudiant.repository.StudentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StudentService {
    private final StudentRepository studentRepository;

    public Student create(Student student) {
        Assert.notNull(student, "Student must not be null");
        log.info("Creating new student");

        studentRepository.findByEmail(student.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException("Student with email " + student.getEmail() + " already exists");
        });
        return studentRepository.save(student);
    }

    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    public Student findById(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> new StudentNotFoundException(id));
    }

    public Student update(Long id, Student updatedStudent) {
        Student existingStudent = findById(id);

        // Only reject the email if it belongs to a *different* student — updating a student
        // with its own unchanged email must not trip the uniqueness check.
        Optional<Student> studentWithSameEmail = studentRepository.findByEmail(updatedStudent.getEmail());
        if (studentWithSameEmail.isPresent() && !studentWithSameEmail.get().getId().equals(id)) {
            throw new IllegalArgumentException("Student with email " + updatedStudent.getEmail() + " already exists");
        }

        existingStudent.setFirstName(updatedStudent.getFirstName());
        existingStudent.setLastName(updatedStudent.getLastName());
        existingStudent.setEmail(updatedStudent.getEmail());
        existingStudent.setBirthDate(updatedStudent.getBirthDate());
        return studentRepository.save(existingStudent);
    }

    public void delete(Long id) {
        Student student = findById(id);
        studentRepository.delete(student);
    }

}
