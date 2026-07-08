package server.club;

import server.network.router.MessageRouter;

/**
 * 社团具体实现类
 * 继承自抽象类Club，实现broadcastMessage方法
 */
public class DefaultClub extends Club {
    
    public DefaultClub(String name, String id, MessageRouter messageRouter) {
        super(name, id, messageRouter);
    }
    
    @Override
    public void broadcastMessage(String message) {
        MessageRouter router = super.getMessageRouter();
        if (router != null) {
            router.broadcastToClub(getId(), message);
        }
    }
}