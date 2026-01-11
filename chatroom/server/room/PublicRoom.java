package server.room;
import server.network.router.MessageRouter;

public class PublicRoom extends Room {
    public PublicRoom(String name, String id, MessageRouter messageRouter) {
        super(name, id, messageRouter);
    }
    
    @Override
    public void broadcastMessage(String message) {
        if (message == null || message.isEmpty()) {
            System.err.println("无效的广播消息");
            return;
        }
        
        System.out.println("公共房间" + getName() + " (ID: " + getId() + ") 正在广播消息: " + message);
        
        // 调用消息路由器进行广播
        getMessageRouter().broadcastToRoom(getId(), message);
    }
}
