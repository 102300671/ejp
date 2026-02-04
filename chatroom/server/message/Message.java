package server.message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private final MessageType type;
    private final String from;
    private final String to;
    private final String content;
    private final String time;
    private final boolean isNSFW;
    private final String iv;
    
    // 日期时间格式化器
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 构造消息对象
     * @param type 消息类型
     * @param from 发送者
     * @param to 接收者
     * @param content 消息内容
     * @param time 发送时间
     */
    public Message(MessageType type, String from, String to, String content, String time) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.content = content;
        this.time = time;
        this.isNSFW = false;
        this.iv = null;
    }
    
    /**
     * 构造消息对象
     * @param type 消息类型
     * @param from 发送者
     * @param to 接收者
     * @param content 消息内容
     * @param time 发送时间
     * @param isNSFW 是否为NSFW内容
     */
    public Message(MessageType type, String from, String to, String content, String time, boolean isNSFW) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.content = content;
        this.time = time;
        this.isNSFW = isNSFW;
        this.iv = null;
    }
    
    /**
     * 构造消息对象
     * @param type 消息类型
     * @param from 发送者
     * @param to 接收者
     * @param content 消息内容
     * @param time 发送时间
     * @param isNSFW 是否为NSFW内容
     * @param iv 加密初始化向量
     */
    public Message(MessageType type, String from, String to, String content, String time, boolean isNSFW, String iv) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.content = content;
        this.time = time;
        this.isNSFW = isNSFW;
        this.iv = iv;
    }
    
    /**
     * 构造消息对象，自动生成当前时间
     * @param type 消息类型
     * @param from 发送者
     * @param to 接收者
     * @param content 消息内容
     */
    public Message(MessageType type, String from, String to, String content) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.content = content;
        this.time = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        this.isNSFW = false;
        this.iv = null;
    }
    
    // Getter方法
    public MessageType getType() {
        return type;
    }
    
    public String getFrom() {
        return from;
    }
    
    public String getTo() {
        return to;
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
    
    /**
     * 获取消息的字符串表示
     * @return 消息的字符串表示
     */
    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", content='" + content + '\'' +
                ", time='" + time + '\'' +
                ", isNSFW=" + isNSFW +
                ", iv='" + iv + '\'' +
                '}';
    }
}