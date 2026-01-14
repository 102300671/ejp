package server.message;
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
            System.err.println("尝试编码空消息对象");
            return null;
        }
        
        try {
            String jsonString = GSON.toJson(message);
            
            // 验证生成的JSON字符串是否有效
            if (jsonString == null || !jsonString.trim().startsWith("{")) {
                System.err.println("生成的JSON字符串无效: " + jsonString);
                return null;
            }
            
            System.out.println("消息编码成功: " + jsonString);
            return jsonString;
        } catch (Exception e) {
            System.err.println("消息编码失败: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("尝试解码空的JSON字符串");
            return null;
        }
        
        try {
            // 直接使用原始字符串进行JSON解析
            Message message = GSON.fromJson(jsonString, Message.class);
            System.out.println("消息解码成功: " + message);
            return message;
        } catch (JsonSyntaxException e) {
            System.err.println("JSON语法错误，解码失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("消息解码失败: " + e.getMessage());
            e.printStackTrace();
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