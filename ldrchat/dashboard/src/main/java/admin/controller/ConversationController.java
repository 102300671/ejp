package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import admin.service.ConversationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    
    @Autowired
    private ConversationService conversationService;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllConversations() {
        return ResponseEntity.ok(conversationService.getAllConversations());
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getConversationStats() {
        return ResponseEntity.ok(conversationService.getConversationStats());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getConversationById(@PathVariable int id) {
        Map<String, Object> conversation = conversationService.getConversationById(id);
        if (conversation != null) {
            return ResponseEntity.ok(conversation);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/type/{type}")
    public ResponseEntity<List<Map<String, Object>>> getConversationsByType(@PathVariable String type) {
        return ResponseEntity.ok(conversationService.getConversationsByType(type));
    }
    
    @GetMapping("/rooms")
    public ResponseEntity<List<Map<String, Object>>> getRoomConversations() {
        return ResponseEntity.ok(conversationService.getRoomConversations());
    }
    
    @GetMapping("/friends")
    public ResponseEntity<List<Map<String, Object>>> getFriendConversations() {
        return ResponseEntity.ok(conversationService.getFriendConversations());
    }
    
    @GetMapping("/temp")
    public ResponseEntity<List<Map<String, Object>>> getTempConversations() {
        return ResponseEntity.ok(conversationService.getTempConversations());
    }
    
    @GetMapping("/user/{username}")
    public ResponseEntity<List<Map<String, Object>>> getUserConversations(@PathVariable String username) {
        return ResponseEntity.ok(conversationService.getUserConversations(username));
    }
    
    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getConversationMembers(@PathVariable int id) {
        return ResponseEntity.ok(conversationService.getConversationMembers(id));
    }
    
    @PostMapping
    public ResponseEntity<String> createConversation(@RequestBody Map<String, String> request) {
        String type = request.get("type");
        String name = request.get("name");
        
        if (type == null || name == null) {
            return ResponseEntity.badRequest().body("会话类型和名称不能为空");
        }
        
        boolean success = conversationService.createConversation(type, name);
        if (success) {
            return ResponseEntity.ok("会话创建成功");
        }
        return ResponseEntity.badRequest().body("会话创建失败");
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteConversation(@PathVariable int id) {
        boolean success = conversationService.deleteConversation(id);
        if (success) {
            return ResponseEntity.ok("会话删除成功");
        }
        return ResponseEntity.badRequest().body("会话删除失败");
    }
    
    @PostMapping("/{id}/members")
    public ResponseEntity<String> addMember(@PathVariable int id, @RequestBody Map<String, String> request) {
        String username = request.get("username");
        String role = request.get("role");
        
        if (username == null || role == null) {
            return ResponseEntity.badRequest().body("用户名和角色不能为空");
        }
        
        boolean success = conversationService.addMember(id, username, role);
        if (success) {
            return ResponseEntity.ok("成员添加成功");
        }
        return ResponseEntity.badRequest().body("成员添加失败");
    }
    
    @DeleteMapping("/{id}/members/{username}")
    public ResponseEntity<String> removeMember(@PathVariable int id, @PathVariable String username) {
        boolean success = conversationService.removeMember(id, username);
        if (success) {
            return ResponseEntity.ok("成员移除成功");
        }
        return ResponseEntity.badRequest().body("成员移除失败");
    }
    
    @PutMapping("/{id}/members/{username}/role")
    public ResponseEntity<String> updateMemberRole(@PathVariable int id, @PathVariable String username, @RequestBody Map<String, String> request) {
        String newRole = request.get("role");
        
        if (newRole == null) {
            return ResponseEntity.badRequest().body("角色不能为空");
        }
        
        boolean success = conversationService.updateMemberRole(id, username, newRole);
        if (success) {
            return ResponseEntity.ok("角色更新成功");
        }
        return ResponseEntity.badRequest().body("角色更新失败");
    }
}
