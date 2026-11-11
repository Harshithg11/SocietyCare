package com.societycare.service.impl;

import com.societycare.dto.request.ProfileUpdateRequest;
import com.societycare.dto.request.UserCreateRequest;
import com.societycare.dto.response.UserResponse;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.Role;
import com.societycare.exception.BusinessRuleException;
import com.societycare.exception.DuplicateResourceException;
import com.societycare.exception.ResourceNotFoundException;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import com.societycare.service.CurrentUserService;
import com.societycare.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SocietyRepository societyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    @Override
    public UserResponse getCurrentProfile() {
        return UserResponse.from(currentUserService.getCurrentUser());
    }

    /** Only name and phone can change. Role, email and society are never editable through this method. */
    @Override
    @Transactional
    public UserResponse updateProfile(ProfileUpdateRequest request) {
        User user = currentUserService.getCurrentUser();
        user.setFullName(request.fullName().trim());
        user.setPhoneNumber(request.phoneNumber());
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
        return UserResponse.from(user);
    }

    /** The protected way (ADMIN only) to create SERVICE_PROVIDER or additional ADMIN accounts. */
    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        Society society = null;
        if (request.societyId() != null) {
            society = societyRepository.findById(request.societyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Society not found with id " + request.societyId()));
        }
        if (request.role() != Role.ADMIN && society == null) {
            throw new BusinessRuleException("Citizens and service providers must belong to a society.");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        user.setSociety(society);
        return UserResponse.from(userRepository.save(user));
    }
}
