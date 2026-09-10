package com.openclassrooms.etudiant.exception;

// Mapped to HTTP 404 by RestExceptionHandler
public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(Long id) {
        super("Student with id " + id + " not found");
    }

}
