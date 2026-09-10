package com.openclassrooms.etudiant.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StudentResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate birthDate;

}
