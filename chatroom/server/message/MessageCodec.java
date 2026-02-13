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
            
            // 优化日志输出：对于HISTORY_RESPONSE类型，简化输出
            if ("HISTORY_RESPONSE".equals(message.getType().toString()) && message.getContent() != null) {
                try {
                    // 尝试解析content字段中的JSON数组
                    com.google.gson.JsonArray jsonArray = GSON.fromJson(message.getContent(), com.google.gson.JsonArray.class);
                    System.out.println("消息编码成功: {\"type\":\"HISTORY_RESPONSE\",\"from\":\"" + message.getFrom() + "\",\"content\":[... " + jsonArray.size() + " messages ...],\"time\":\"" + message.getTime() + "\"}");
                } catch (Exception e) {
                    // 如果解析失败，使用原始输出
                    System.out.println("消息编码成功: " + jsonString);
                }
            } else {
                System.out.println("消息编码成功: " + jsonString);
            }
            
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
        if (jsonString == null || jsonString.trim().isEmpty()) {
            System.err.println("尝试解码空的JSON字符串");
            return null;
        }
        
        try {
            // 清理字符串，移除可能的非打印字符和BOM标记
            String cleanJson = jsonString.trim();
            
            // 检查是否以{开头，确保是有效的JSON对象
            if (!cleanJson.startsWith("{")) {
                System.err.println("无效的JSON格式，必须以{开头: " + cleanJson);
                return null;
            }
            
            // 直接使用清理后的字符串进行JSON解析
            Message message = GSON.fromJson(cleanJson, Message.class);
            
            // 验证解码后的消息对象是否有效
            if (message == null) {
                System.err.println("JSON解析返回null: " + cleanJson);
                return null;
            }
            
            // 验证消息类型
            if (message.getType() == null) {
                System.err.println("消息类型为空: " + message);
                return null;
            }
            
            System.out.println("消息解码成功: " + message);
            return message;
        } catch (JsonSyntaxException e) {
            System.err.println("JSON语法错误，解码失败: " + e.getMessage());
            System.err.println("原始字符串: " + jsonString);
            return null;
        } catch (Exception e) {
            System.err.println("消息解码失败: " + e.getMessage());
            System.err.println("原始字符串: " + jsonString);
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
    
    /**
     * 将消息列表编码为JSON字符串
     * @param messages 要编码的消息列表
     * @return JSON格式的消息列表字符串
     */
    public String encodeMessages(java.util.List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            System.err.println("尝试编码空消息列表");
            return "[]";
        }
        
        try {
            String jsonString = GSON.toJson(messages);
            System.out.println("消息列表编码成功: " + jsonString);
            return jsonString;
        } catch (Exception e) {
            System.err.println("消息列表编码失败: " + e.getMessage());
            e.printStackTrace();
            return "[]";
        }
    }
}