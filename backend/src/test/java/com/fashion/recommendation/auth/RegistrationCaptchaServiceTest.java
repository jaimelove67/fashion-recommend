package com.fashion.recommendation.auth;

import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationCaptchaServiceTest {
    private final Clock clock = mock(Clock.class);
    private final SecureRandom random = mock(SecureRandom.class);
    private final Instant now = Instant.parse("2026-10-16T00:00:00Z");
    private RegistrationCaptchaService service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        when(random.nextInt(anyInt())).thenReturn(0);
        service = new RegistrationCaptchaService(clock, random);
    }

    @Test
    void createsAnActualPngAndOnlyReturnsTheImageAndLifetime() throws Exception {
        var response = service.create(new MockHttpSession());
        assertEquals(180, response.expiresInSeconds());
        assertTrue(response.image().startsWith("data:image/png;base64,"));
        var png = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(response.image().split(",", 2)[1])));
        assertNotNull(png);
        assertEquals(160, png.getWidth());
        assertEquals(52, png.getHeight());
    }

    @Test
    void acceptsCaseInsensitiveInputAndConsumesTheChallenge() {
        when(random.nextInt(anyInt())).thenAnswer(call -> (int) call.getArgument(0) > 8 ? 8 : 0);
        var session = new MockHttpSession();
        service.create(session);
        service.verifyAndConsume(session, " aaaaa ");
        assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(session, "AAAAA"));
    }

    @Test
    void wrongInputAlsoConsumesTheChallenge() {
        var session = new MockHttpSession();
        service.create(session);
        assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(session, "33333"));
        assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(session, "22222"));
    }

    @Test
    void rejectsMissingAndOtherSessions() {
        service.create(new MockHttpSession());
        assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(null, "22222"));
        assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(new MockHttpSession(), "22222"));
    }

    @Test
    void rejectsTheChallengeAtItsExpiryBoundary() {
        var session = new MockHttpSession();
        service.create(session);
        when(clock.instant()).thenReturn(now.plusSeconds(180));
        var error = assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(session, "22222"));
        assertTrue(error.getReason().contains("过期"));
    }

    @Test
    void refreshingReplacesTheOldChallenge() {
        var session = new MockHttpSession();
        var oldImage = service.create(session).image();
        when(random.nextInt(anyInt())).thenReturn(1);
        assertFalse(oldImage.equals(service.create(session).image()));
        assertThrows(ResponseStatusException.class, () -> service.verifyAndConsume(session, "22222"));
        service.create(session);
        service.verifyAndConsume(session, "33333");
    }

    @Test
    void concurrentSubmissionsCannotConsumeTheSameChallengeTwice() throws Exception {
        var session = new MockHttpSession();
        service.create(session);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var attempts = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                start.await();
                try {
                    service.verifyAndConsume(session, "22222");
                    return 1;
                } catch (ResponseStatusException exception) {
                    return 0;
                }
            })).toList();
            start.countDown();
            assertEquals(1, attempts.get(0).get() + attempts.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }
}
