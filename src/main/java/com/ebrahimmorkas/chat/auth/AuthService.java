package com.ebrahimmorkas.chat.auth;

import com.ebrahimmorkas.chat.common.ConflictException;
import com.ebrahimmorkas.chat.user.User;
import com.ebrahimmorkas.chat.user.UserRepository;
import com.ebrahimmorkas.chat.user.UserResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Compared against when the email is unknown, so response time doesn't reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("timing-attack-mitigation");
    }

    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        try {
            User user = new User(email, passwordEncoder.encode(request.password()), request.displayName().trim());
            return UserResponse.from(userRepository.save(user));
        } catch (DuplicateKeyException e) {
            // Concurrent registration with the same email; the unique index decided
            throw new ConflictException("An account with this email already exists");
        }
    }

    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return tokenService.issue(user.get());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
