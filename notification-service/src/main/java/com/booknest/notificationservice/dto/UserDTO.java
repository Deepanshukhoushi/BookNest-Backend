package com.booknest.notificationservice.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private Boolean suspended;
}
