package com.fashion.recommendation.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final JdbcUserDetailsManager users;
    private final PasswordEncoder passwordEncoder;
    private final boolean registrationEnabled;

    public AuthService(
            JdbcUserDetailsManager users,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.registration-enabled:true}") boolean registrationEnabled) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.registrationEnabled = registrationEnabled;
    }

    @Transactional
    public AuthUserResponse register(AuthRegistrationRequest request) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前未开放账号注册");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能超过 72 个字节");
        }
        if (users.userExists(request.username())) {
            throw usernameConflict();
        }

        try {
            users.createUser(User.withUsername(request.username())
                    .password(passwordEncoder.encode(request.password()))
                    .roles("USER")
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw usernameConflict();
        }
        return new AuthUserResponse(request.username());
    }

    private static ResponseStatusException usernameConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
    }
}
