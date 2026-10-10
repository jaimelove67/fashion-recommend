package com.fashion.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImage;
import com.fashion.recommendation.storage.StoredImageData;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TryOnFavoriteControllerTest {
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0S8AAAAASUVORK5CYII=");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockBean ImageStorage storage;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM try_on_favorites");
        given(storage.store(anyString(), any())).willReturn(new StoredImage("favorites/test.png", "image/png"));
        given(storage.read("favorites/test.png")).willReturn(new StoredImageData(PNG, "image/png"));
    }

    private MockHttpServletRequestBuilder save(String username, byte[] image, String type, String source) {
        return multipart("/api/v1/me/try-on-favorites")
                .file(new MockMultipartFile("image", "garment.png", type, image))
                .param("name", "购物白衬衫").param("category", "上装").param("sourceKind", "SHOPPING")
                .param("expectedUser", username).param("sourceUrl", source);
    }

    @Test void storesOnlyTheAuthenticatedUsersFavoriteAndRetriesWithoutDuplicates() throws Exception {
        String result = mvc.perform(save("favorite-owner", PNG, "image/png", "https://shop.example/item/1")
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageObjectKey").doesNotExist()).andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(result).path("data").path("id").asLong();
        mvc.perform(save("favorite-owner", PNG, "image/png", "https://shop.example/item/1")
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM try_on_favorites", Integer.class));
        mvc.perform(get("/api/v1/me/try-on-favorites").with(user("someone-else")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/me/try-on-favorites/" + id + "/image").with(user("someone-else")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/try-on-favorites/" + id + "/image").with(user("favorite-owner")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/me/try-on-favorites")).andExpect(status().isUnauthorized());
        mvc.perform(save("favorite-owner", PNG, "image/png", "https://shop.example/item/1")
                .with(user("favorite-owner"))).andExpect(status().isForbidden());
        verify(storage, never()).store(anyString(), any());
    }

    @Test void rejectsAnAccountChangeBetweenConnectingAndSaving() throws Exception {
        mvc.perform(save("old-account", PNG, "image/png", "https://shop.example/item/1")
                .with(user("new-account")).with(csrf())).andExpect(status().isConflict());
        verify(storage, never()).store(anyString(), any());
    }

    @Test void rejectsExecutableLinksAndFakeImageContent() throws Exception {
        mvc.perform(save("favorite-owner", PNG, "image/png", "javascript:alert(1)")
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(save("favorite-owner", "<html>not an image</html>".getBytes(), "image/png", "https://shop.example/item/1")
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(save("favorite-owner", PNG, "image/jpeg", "https://shop.example/item/1")
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isBadRequest());
        verify(storage, never()).store(anyString(), any());
    }

    @Test void rejectsUnsupportedCategoriesAndEmptyImages() throws Exception {
        mvc.perform(save("favorite-owner", PNG, "image/png", "https://shop.example/item/1").with(request -> { request.setParameter("category", "鞋履"); return request; })
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(save("favorite-owner", new byte[0], "image/png", "https://shop.example/item/1")
                .with(user("favorite-owner")).with(csrf())).andExpect(status().isBadRequest());
    }
}
