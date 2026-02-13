package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import admin.service.MessageService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    
    @Autowired
    private MessageService messageService;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllMessages(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(messageService.getAllMessages(limit, offset));
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getMessageStats() {
        return ResponseEntity.ok(messageService.getMessageStats());
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchMessages(
            @RequestParam String term,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.searchMessages(term, limit));
    }
    
    @GetMapping("/room/{roomName}")
    public ResponseEntity<List<Map<String, Object>>> getRoomMessages(
            @PathVariable String roomName,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getRoomMessages(roomName, limit));
    }
    
    @GetMapping("/private")
    public ResponseEntity<List<Map<String, Object>>> getPrivateMessages(
            @RequestParam String user1,
            @RequestParam String user2,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getPrivateMessages(user1, user2, limit));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMessage(@PathVariable int id) {
        boolean success = messageService.deleteMessage(id);
        if (success) {
            return ResponseEntity.ok("消息删除成功");
        }
        return ResponseEntity.badRequest().body("消息删除失败");
    }
    
    @GetMapping("/top-users")
    public ResponseEntity<List<Map<String, Object>>> getTopUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(messageService.getTopUsersByMessageCount(limit));
    }
    
    @GetMapping("/top-rooms")
    public ResponseEntity<List<Map<String, Object>>> getTopRooms(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(messageService.getTopRoomsByMessageCount(limit));
    }
}
