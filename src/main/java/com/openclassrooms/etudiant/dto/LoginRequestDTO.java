package com.openclassrooms.etudiant.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String login;
    private String password;
}
