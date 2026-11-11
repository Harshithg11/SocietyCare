package com.societycare.service.impl;

import com.societycare.dto.request.LoginRequest;
import com.societycare.dto.request.RegisterRequest;
import com.societycare.dto.response.AuthResponse;
import com.societycare.dto.response.UserResponse;
import com.societycare.entity.Society;
import com.societycare.entity.User;
import com.societycare.enums.Role;
import com.societycare.exception.DuplicateResourceException;
import com.societycare.exception.ResourceNotFoundException;
import com.societycare.exception.UnauthorizedOperationException;
import com.societycare.repository.SocietyRepository;
import com.societycare.repository.UserRepository;
import com.societycare.security.JwtService;
import com.societycare.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final SocietyRepository societyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * Public registration. Only CITIZEN (default) and SERVICE_PROVIDER are allowed.
     * ADMIN can never be obtained here; admins are created by the dev seed or by an existing admin.
     */
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        Role role = request.role() == null ? Role.CITIZEN : request.role();
        if (role == Role.ADMIN) {
            throw new UnauthorizedOperationException("Admin accounts cannot be created through public registration.");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }
        Society society = societyRepository.findById(request.societyId())
                .orElseThrow(() -> new ResourceNotFoundException("Society not found with id " + request.societyId()));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(role);
        user.setSociety(society);

        User saved = userRepository.save(user);
        log.info("Registered new {} account with id {}", role, saved.getId());
        return UserResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        // Throws an AuthenticationException (answered with HTTP 401) when the credentials are wrong.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        long ttlMs = jwtService.getExpirationMs();
        return new AuthResponse(token, "Bearer", ttlMs / 1000, Instant.now().plusMillis(ttlMs),
                user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
