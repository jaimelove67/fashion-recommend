package com.fashion.recommendation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieConfigurationTest {
    // Load production YAML explicitly because the test application.yml replaces it on the classpath.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=file:src/main/resources/application.yml")
            .withUserConfiguration(CookieConfiguration.class);

    @Test
    void defaultsToSecureSessionCookies() {
        contextRunner.run(context -> {
            var cookie = context.getBean(ServerProperties.class).getServlet().getSession().getCookie();
            assertThat(cookie.getSecure()).isTrue();
            assertThat(cookie.getHttpOnly()).isTrue();
        });
    }

    @Test
    void allowsExplicitLocalHttpOverride() {
        contextRunner.withPropertyValues("SESSION_COOKIE_SECURE=false").run(context -> {
            var cookie = context.getBean(ServerProperties.class).getServlet().getSession().getCookie();
            assertThat(cookie.getSecure()).isFalse();
            assertThat(cookie.getHttpOnly()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServerProperties.class)
    static class CookieConfiguration {
    }
}
