package com.fashion.recommendation.auth;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class CaptchaTestConfiguration {
    @Bean
    @Primary
    RegistrationCaptchaService deterministicCaptchaService() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(anyInt())).thenAnswer(call -> (int) call.getArgument(0) > 8 ? 8 : 0);
        return new RegistrationCaptchaService(Clock.systemUTC(), random);
    }
}
