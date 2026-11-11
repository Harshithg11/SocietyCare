package com.societycare.service;

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
import com.societycare.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SocietyRepository societyRepository;
    @Mock
    private AuthenticationManager authenticationManager;

    // Real BCrypt and real JwtService: we want to verify real hashing and real tokens.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService("unit-test-secret-key-with-more-than-32-chars", 3_600_000L);

    private AuthServiceImpl authService;
    private Society society;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, societyRepository, passwordEncoder,
                authenticationManager, jwtService);
        society = new Society();
        society.setId(1L);
        society.setSocietyName("Green Valley");

        when(societyRepository.findById(1L)).thenReturn(Optional.of(society));
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
    }

    private RegisterRequest request(Role role) {
        return new RegisterRequest("Priya Sharma", "  Priya@Example.com ", "Priya@1234", "9876543210", 1L, role);
    }

    @Test
    void registerCreatesCitizenWithBcryptHashedPassword() {
        UserResponse response = authService.register(request(null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getRole()).isEqualTo(Role.CITIZEN);
        assertThat(saved.getEmail()).isEqualTo("priya@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("Priya@1234");
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("Priya@1234", saved.getPassword())).isTrue();
        assertThat(response.role()).isEqualTo(Role.CITIZEN);
        assertThat(response.societyId()).isEqualTo(1L);
    }

    @Test
    void registerAllowsServiceProvider() {
        UserResponse response = authService.register(request(Role.SERVICE_PROVIDER));
        assertThat(response.role()).isEqualTo(Role.SERVICE_PROVIDER);
    }

    @Test
    void registerRejectsAdminRole() {
        assertThatThrownBy(() -> authService.register(request(Role.ADMIN)))
                .isInstanceOf(UnauthorizedOperationException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("priya@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request(null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsUnknownSociety() {
        when(societyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void loginReturnsValidJwtForCorrectCredentials() {
        User user = new User();
        user.setId(5L);
        user.setFullName("Citizen One");
        user.setEmail("citizen1@societycare.local");
        user.setPassword(passwordEncoder.encode("Password@123"));
        user.setRole(Role.CITIZEN);
        when(userRepository.findByEmail("citizen1@societycare.local")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));

        AuthResponse response = authService.login(new LoginRequest("Citizen1@SocietyCare.local", "Password@123"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.role()).isEqualTo(Role.CITIZEN);
        assertThat(response.userId()).isEqualTo(5L);
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(jwtService.extractUsername(response.token())).isEqualTo("citizen1@societycare.local");
    }

    @Test
    void loginFailsForInvalidCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("citizen1@societycare.local", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
