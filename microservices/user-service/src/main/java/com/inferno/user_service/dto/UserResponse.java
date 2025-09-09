package com.inferno.user_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String uuid;
    private String name;
    private String lastName;
    private String email;
    private String document;
    private String imageUrl;
    private String createdAt;
    private String updatedAt;
}

