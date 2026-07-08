package client.cli.message;

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
    
    private static final DateTimeFormatter BEIJING_ZONE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final java.time.ZoneId ASIA_SHANGHAI = java.time.ZoneId.of("Asia/Shanghai");
    
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
    
    public Message(MessageType type, String from, String content) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.time = ZonedDateTime.now(ASIA_SHANGHAI).format(BEIJING_ZONE);
        this.isNSFW = false;
        this.iv = null;
        this.id = null;
        this.conversationId = null;
    }
    
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
