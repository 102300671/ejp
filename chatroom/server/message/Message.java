package server.message;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private final MessageType type;
    private final String from;
    private final String content;
    private final String time;
    private final boolean isNSFW;
    private final String iv;
    private final String id;
    private final Integer conversationId;
    
    // 日期时间格式化器 - 使用北京时间
    private static final DateTimeFormatter BEIJING_ZONE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final java.time.ZoneId ASIA_SHANGHAI = java.time.ZoneId.of("Asia/Shanghai");
    
    /**
     * 构造消息对象（带会话ID）
     * @param type 消息类型
     * @param from 发送者
     * @param content 消息内容
     * @param time 发送时间
     * @param conversationId 会话ID
     */
    public Message(MessageType type, String from, String content, String time, Integer conversationId) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.time = time;
        this.isNSFW = false;
        this.iv = null;
        this.id = null;
        this.conversationId = conversationId;
    }
    
    /**
     * 构造消息对象（带会话ID，完整参数）
     * @param type 消息类型
     * @param from 发送者
     * @param content 消息内容
     * @param time 发送时间
     * @param isNSFW 是否为NSFW内容
     * @param iv 加密初始化向量
     * @param id 消息ID
     * @param conversationId 会话ID
     */
    public Message(MessageType type, String from, String content, String time, boolean isNSFW, String iv, String id, Integer conversationId) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.time = time;
        this.isNSFW = isNSFW;
        this.iv = iv;
        this.id = id;
        this.conversationId = conversationId;
    }
    
    /**
     * 构造消息对象，自动生成当前时间（带会话ID）
     * @param type 消息类型
     * @param from 发送者
     * @param content 消息内容
     * @param conversationId 会话ID
     */
    public Message(MessageType type, String from, String content, Integer conversationId) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.time = ZonedDateTime.now(ASIA_SHANGHAI).format(BEIJING_ZONE);
        this.isNSFW = false;
        this.iv = null;
        this.id = null;
        this.conversationId = conversationId;
    }
    
    // Getter方法
    public MessageType getType() {
        return type;
    }
    
    public String getFrom() {
        return from;
    }
    
    public String getContent() {
        return content;
    }
    
    public String getTime() {
        return time;
    }
    
    public boolean isNSFW() {
        return isNSFW;
    }
    
    public String getIv() {
        return iv;
    }
    
    public String getId() {
        return id;
    }
    
    public Integer getConversationId() {
        return conversationId;
    }
    
    /**
     * 获取消息的字符串表示
     * @return 消息的字符串表示
     */
    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", from='" + from + '\'' +
                ", content='" + content + '\'' +
                ", time='" + time + '\'' +
                ", isNSFW=" + isNSFW +
                ", iv='" + iv + '\'' +
                ", id='" + id + '\'' +
                ", conversationId=" + conversationId +
                '}';
    }
}