public class Message {
  private MessageType type;
  private String from;
  private String to;
  private String content;
  private String time;
  public Message(MessageType type, String from, String to, String content, String time) {
    this.type = type;
    this.from = from;
    this.to = to;
    this.content = content;
    this.time = time;
  }
  public void setMessageType(MessageType type) {
    this.type = type;
  }
  public void setFrom(String from) {
    this.from = from;
  }
  public void setTo(String to) {
    this.to = to;
  }
  public void setContent(String content) {
    this.content = content;
  }
  public void time(String time) {
    this.time = time 
  }
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
}