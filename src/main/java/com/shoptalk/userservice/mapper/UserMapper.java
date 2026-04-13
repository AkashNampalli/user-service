package com.shoptalk.userservice.mapper;

import com.shoptalk.userservice.dto.RegisterRequest;
import com.shoptalk.userservice.dto.UserResponse;
import com.shoptalk.userservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(RegisterRequest request){
        return User.builder().username(request.getUsername()).
                email(request.getEmail())
                .password(request.getPassword()).mobileNumber(request.getMobileNumber())
                        .role(request.getRole()).build();
    }

    public UserResponse toResponse(User user){
        return UserResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .id(user.getId())
                .mobileNumber(user.getMobileNumber())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt()).build();
    }
}
