package com.shoptalk.userservice.dto;

import com.shoptalk.userservice.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private String username;
    private String email;
    private UserRole role;
    private UUID id;
    private String mobileNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
