package client.javafx.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class ZFileServiceResponseParsingTest {

    @Test
    void shouldWrapPrimitiveStringResponseIntoCompatibleJsonObject() throws Exception {
        String body = "\"/avatars/users/root/avatar.png\"";

        JsonObject response = ZFileService.parseResponsePayload(body, "创建上传任务");

        assertNotNull(response);
        assertEquals("0", response.get("code").getAsString());
        assertEquals("/avatars/users/root/avatar.png", response.get("data").getAsString());
    }

    @Test
    void shouldKeepObjectPayloadAsObject() throws Exception {
        String body = "{\"code\":0,\"data\":\"/avatars/users/root/avatar.png\"}";

        JsonObject response = ZFileService.parseResponsePayload(body, "创建上传任务");

        assertEquals("0", response.get("code").getAsString());
        assertEquals("/avatars/users/root/avatar.png", response.get("data").getAsString());
    }
}
