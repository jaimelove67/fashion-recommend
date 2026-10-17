package com.fashion.recommendation.auth;

import jakarta.servlet.http.HttpSession;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationCaptchaService {
    private static final String SESSION_KEY = RegistrationCaptchaService.class.getName() + ".challenge";
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 5;
    private static final Duration LIFETIME = Duration.ofMinutes(3);

    private final Clock clock;
    private final SecureRandom random;

    public RegistrationCaptchaService() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    RegistrationCaptchaService(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public CaptchaResponse create(HttpSession session) {
        StringBuilder answer = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            answer.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        String image = render(answer.toString());
        synchronized (session) {
            session.setAttribute(SESSION_KEY, new Challenge(digest(answer.toString()), clock.instant().plus(LIFETIME)));
        }
        return new CaptchaResponse(image, (int) LIFETIME.toSeconds());
    }

    public void verifyAndConsume(HttpSession session, String answer) {
        if (session == null) throw invalid("验证码已失效，请刷新后重试。");
        Challenge challenge;
        synchronized (session) {
            Object stored = session.getAttribute(SESSION_KEY);
            session.removeAttribute(SESSION_KEY);
            challenge = stored instanceof Challenge value ? value : null;
        }
        if (challenge == null) throw invalid("验证码已失效，请刷新后重试。");
        if (!clock.instant().isBefore(challenge.expiresAt())) {
            throw invalid("验证码已过期，请刷新后重试。");
        }
        String normalized = answer == null ? "" : answer.strip().toUpperCase(Locale.ROOT);
        if (!MessageDigest.isEqual(challenge.answerHash(), digest(normalized))) {
            throw invalid("验证码错误，请输入新图片中的字符。");
        }
    }

    private String render(String answer) {
        BufferedImage image = new BufferedImage(160, 52, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(247, 245, 238));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(184, 196, 174));
            for (int index = 0; index < 5; index++) {
                graphics.drawLine(random.nextInt(160), random.nextInt(52), random.nextInt(160), random.nextInt(52));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            for (int index = 0; index < answer.length(); index++) {
                graphics.setColor(new Color(45 + random.nextInt(55), 65 + random.nextInt(45), 45 + random.nextInt(50)));
                var transform = graphics.getTransform();
                graphics.rotate((random.nextDouble() - 0.5) * 0.3, 20 + index * 28, 28);
                graphics.drawString(answer.substring(index, index + 1), 10 + index * 28, 37 + random.nextInt(6));
                graphics.setTransform(transform);
            }
            graphics.setColor(new Color(164, 175, 153));
            for (int index = 0; index < 70; index++) {
                graphics.fillOval(random.nextInt(160), random.nextInt(52), 1, 1);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码暂时无法生成，请稍后重试。", exception);
        } finally {
            graphics.dispose();
        }
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Challenge(byte[] answerHash, Instant expiresAt) implements Serializable {
    }

    public record CaptchaResponse(String image, int expiresInSeconds) {
    }
}
