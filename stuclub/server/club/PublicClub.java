package server.club;
import server.network.router.MessageRouter;

public class PublicClub extends Club {
    public PublicClub(String name, String id, MessageRouter messageRouter) {
        super(name, id, messageRouter);
    }
    
    @Override
    public void broadcastMessage(String message) {
        if (message == null || message.isEmpty()) {
            System.err.println("无效的广播消息");
            return;
        }
        
        System.out.println("公共社团" + getName() + " (ID: " + getId() + ") 正在广播消息: " + message);
        
        getMessageRouter().broadcastToClub(getId(), message);
    }
}