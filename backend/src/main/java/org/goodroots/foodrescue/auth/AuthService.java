package org.goodroots.foodrescue.auth;

import org.goodroots.foodrescue.common.ApiException;
import org.goodroots.foodrescue.security.JwtService;
import org.goodroots.foodrescue.user.User;
import org.goodroots.foodrescue.user.UserRepository;
import org.goodroots.foodrescue.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw ApiException.forbidden("Administrator accounts cannot be created through public registration.");
        }
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw ApiException.conflict("An account with this email already exists.");
        String organization = request.organizationName() == null || request.organizationName().isBlank()
            ? null : request.organizationName().trim();
        User user = users.save(new User(request.fullName().trim(), organization, email,
            passwordEncoder.encode(request.password()), request.role()));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = users.findByEmail(email)
            .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Email or password is incorrect.");
        }
        return toResponse(user);
    }

    private AuthDtos.AuthResponse toResponse(User user) {
        return new AuthDtos.AuthResponse(jwtService.issue(user), user.getId(), user.getFullName(),
            user.getOrganizationName(), user.getEmail(), user.getRole());
    }
}
