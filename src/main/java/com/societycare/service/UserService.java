package com.societycare.service;

import com.societycare.dto.request.ProfileUpdateRequest;
import com.societycare.dto.request.UserCreateRequest;
import com.societycare.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse getCurrentProfile();

    UserResponse updateProfile(ProfileUpdateRequest request);

    Page<UserResponse> getAllUsers(Pageable pageable);

    UserResponse getUserById(Long id);

    UserResponse createUser(UserCreateRequest request);
}
