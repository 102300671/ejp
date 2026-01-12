package client.message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class MessageCodec {
    // 静态Gson实例，线程安全，只需要创建一次
    private static final Gson GSON = new GsonBuilder()
            .create();
    
    /**
     * 将Message对象编码为JSON字符串
     * @param message 要编码的消息对象
     * @return JSON格式的消息字符串
     */
    public String encode(Message message) {
        if (message == null) {
            return null;
        }
        
        try {
            return GSON.toJson(message);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 将JSON字符串解码为Message对象
     * @param jsonString 要解码的JSON字符串
     * @return 解码后的消息对象，如果解码失败则返回null
     */
    public Message decode(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        
        try {
            return GSON.fromJson(jsonString, Message.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取Gson实例
     * @return Gson实例
     */
    public static Gson getGson() {
        return GSON;
    }
}