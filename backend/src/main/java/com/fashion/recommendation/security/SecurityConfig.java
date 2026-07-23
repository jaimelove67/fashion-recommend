package com.fashion.recommendation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.auth.AuthUserResponse;
import com.fashion.recommendation.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JdbcUserDetailsManager userDetailsManager(DataSource dataSource) {
        JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);
        users.setUsersByUsernameQuery(
                "SELECT username, password_hash, enabled FROM app_users WHERE username = ?");
        users.setAuthoritiesByUsernameQuery(
                "SELECT username, authority FROM app_authorities WHERE username = ?");
        users.setUserExistsSql("SELECT username FROM app_users WHERE username = ?");
        users.setCreateUserSql(
                "INSERT INTO app_users (username, password_hash, enabled) VALUES (?, ?, ?)");
        users.setCreateAuthoritySql(
                "INSERT INTO app_authorities (username, authority) VALUES (?, ?)");
        return users;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/csrf", "/api/v1/trends", "/api/v1/weather/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).denyAll()
                        .anyRequest().authenticated())
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .formLogin(form -> form
                        .loginPage("/api/v1/auth/login")
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> writeJson(
                                response, objectMapper, HttpStatus.OK,
                                ApiResponse.ok(new AuthUserResponse(authentication.getName()))))
                        .failureHandler((request, response, exception) -> writeJson(
                                response, objectMapper, HttpStatus.UNAUTHORIZED,
                                new ApiResponse<>(401, "用户名或密码错误", null)))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) -> writeJson(
                                response, objectMapper, HttpStatus.OK, ApiResponse.ok(null))))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeJson(
                                response, objectMapper, HttpStatus.UNAUTHORIZED,
                                new ApiResponse<>(401, "请先登录", null)))
                        .accessDeniedHandler((request, response, exception) -> writeJson(
                                response, objectMapper, HttpStatus.FORBIDDEN,
                                new ApiResponse<>(403, "请求被拒绝，请刷新后重试", null))));

        return http.build();
    }

    private static void writeJson(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            ApiResponse<?> body) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
