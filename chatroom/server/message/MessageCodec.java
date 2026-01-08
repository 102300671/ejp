package server.message;
import com.google.gson.*;


public class MessageCodec {
	Gson gson = new Gson();
	public String encode(Message message) {
		return gson.toJson(message);
	}
	public Message decode(String message) {
		return gson.fromJson(message, Message.class);
	}
}