package com.booknest.authservice.dto;

import com.booknest.authservice.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {
    @NotNull(message = "role is required")
    private Role role;
}
