package cn.edu.ncist.chatroom.common.message;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Message implements Serializable {
    private final MessageType type;
    private final String sender;
    private final String receiver;
    private final String content;
    private final long timestamp;
    private final String messageId;

    public static final String SYSTEM_SENDER = "SYSTEM";
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static class Builder {
        private MessageType type;
        private String sender;
        private String receiver;
        private String content;
        private long timestamp;
        private String messageId;
    }
    
    private Message(Builder builder) {
        this.type = builder.type;
        this.sender = builder.sender;
        this.receiver = builder.receiver;
        this.content = builder.content;
        this.timestamp = builder.timestamp;
        this.messageId = builder.messageId != null ? builder.messageId : generateMessageId();
    }
}