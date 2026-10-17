package com.fashion.recommendation;

import com.fashion.recommendation.auth.CaptchaTestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CaptchaTestConfiguration.class)
class AuthenticationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private RequestPostProcessor csrf() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return request -> {
            request.setCookies(result.getResponse().getCookies());
            request.addHeader(token.path("headerName").asText(), token.path("token").asText());
            return request;
        };
    }

    @Test
    void rejectsAnonymousPersonalDataAndManagementMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/me/wardrobe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void registersAuthenticatesEnforcesCsrfAndInvalidatesSessionOnLogout() throws Exception {
        String username = "auth" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "StrongPass123";
        MockHttpSession registrationSession = new MockHttpSession();

        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));

        mockMvc.perform(get("/api/v1/auth/captcha").session(registrationSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .session(registrationSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","captcha":"aaaaa"}
                                """.formatted(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/v1/me/wardrobe")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"未写入衣物","category":"上装","color":"白色"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void captchaIsPublicUncachedAndDoesNotReturnItsAnswer() throws Exception {
        mockMvc.perform(get("/api/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.image").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(180))
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andExpect(jsonPath("$.data.captcha").doesNotExist());
    }

    @Test
    void registrationRejectsMissingCaptchaAndWrongSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing-captcha","password":"StrongPass123"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/captcha").session(new MockHttpSession()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"wrong-session","password":"StrongPass123","captcha":"AAAAA"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证码已失效，请刷新后重试。"));
    }

    @Test
    void wrongCaptchaCannotCreateAnAccountAndTheChallengeCannotBeReused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String username = "captcha" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(get("/api/v1/auth/captcha").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"StrongPass123","captcha":"00000"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证码错误，请输入新图片中的字符。"));
        mockMvc.perform(post("/api/v1/auth/register").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"StrongPass123","captcha":"AAAAA"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .param("username", username).param("password", "StrongPass123"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/captcha").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"StrongPass123","captcha":"AAAAA"}
                                """.formatted(username)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s-replay","password":"StrongPass123","captcha":"AAAAA"}
                                """.formatted(username)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void captchaDoesNotBypassCsrfProtection() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/v1/auth/captcha").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"no-csrf-captcha","password":"StrongPass123","captcha":"AAAAA"}
                                """))
                .andExpect(status().isForbidden());
    }
}
