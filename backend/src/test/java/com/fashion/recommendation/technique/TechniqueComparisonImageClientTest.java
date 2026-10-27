package com.fashion.recommendation.technique;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechniqueComparisonImageClientTest {
    @Test
    void validatesMagicDimensionsAndSizeBeforePublicStorage() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB), "png", output);
        assertEquals("image/png", TechniqueComparisonImageClient.validate(output.toByteArray()).contentType());
        assertThrows(IllegalArgumentException.class, () -> TechniqueComparisonImageClient.validate("<html>not an image</html>".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> TechniqueComparisonImageClient.validate(new byte[TechniqueComparisonImageClient.MAX_BYTES + 1]));
        output.reset();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThrows(IllegalArgumentException.class, () -> TechniqueComparisonImageClient.validate(output.toByteArray()));
    }

    @Test
    void onlyAllowsTheProviderResultCdn() {
        assertTrue(TechniqueComparisonImageClient.allowedHost("dashscope-result-bj.oss-cn-beijing.aliyuncs.com"));
        for (String host : new String[] {"localhost", "127.0.0.1", "evil.test", "bucket.oss-cn-beijing.aliyuncs.com",
                "dashscope-result-bj.oss-cn-beijing.aliyuncs.com.evil.test"})
            assertFalse(TechniqueComparisonImageClient.allowedHost(host));
    }
}
