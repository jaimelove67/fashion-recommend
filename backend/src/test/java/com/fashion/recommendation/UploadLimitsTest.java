package com.fashion.recommendation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

// Real HTTP exercises the servlet upload limit; MockMvc multipart requests bypass it.
@SpringBootTest(classes = UploadLimitsTest.UploadConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=file:src/main/resources/application.yml")
class UploadLimitsTest {
    private static final int MIB = 1024 * 1024;

    @LocalServerPort
    private int port;

    @Test
    void acceptsPhotosUpToTenMebibytesIncludingMultipartOverhead() throws Exception {
        for (int size : new int[] {2 * MIB, 10 * MIB}) {
            var response = upload(size);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(String.valueOf(size));
        }
    }

    @Test
    void rejectsPhotosAboveTheFileLimit() throws Exception {
        assertThat(upload(10 * MIB + 1).statusCode()).isEqualTo(413);
    }

    private HttpResponse<String> upload(int size) throws Exception {
        byte[] prefix = ("--upload-test\r\nContent-Disposition: form-data; name=\"image\"; "
                + "filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = "\r\n--upload-test--\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[prefix.length + size + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(suffix, 0, body, prefix.length + size, suffix.length);
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/upload-limit-probe"))
                .header("Content-Type", "multipart/form-data; boundary=upload-test")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            MultipartAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class})
    @Import(UploadProbe.class)
    static class UploadConfiguration {
    }

    @RestController
    @TestComponent
    static class UploadProbe {
        @PostMapping("/upload-limit-probe")
        long upload(@RequestParam("image") MultipartFile image) {
            return image.getSize();
        }
    }
}
