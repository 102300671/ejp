package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import admin.service.UserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable int id) {
        Map<String, Object> user = userService.getUserById(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchUsers(@RequestParam String term) {
        return ResponseEntity.ok(userService.searchUsers(term));
    }
    
    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("用户名和密码不能为空");
        }
        
        boolean success = userService.createUser(username, password);
        if (success) {
            return ResponseEntity.ok("用户创建成功");
        }
        return ResponseEntity.badRequest().body("用户创建失败");
    }
    
    @PutMapping("/{id}/password")
    public ResponseEntity<String> updateUserPassword(@PathVariable int id, @RequestBody Map<String, String> request) {
        String newPassword = request.get("password");
        
        if (newPassword == null) {
            return ResponseEntity.badRequest().body("密码不能为空");
        }
        
        boolean success = userService.updateUserPassword(id, newPassword);
        if (success) {
            return ResponseEntity.ok("密码更新成功");
        }
        return ResponseEntity.badRequest().body("密码更新失败");
    }
    
    @PutMapping("/{id}/accept-temporary-chat")
    public ResponseEntity<String> updateAcceptTemporaryChat(@PathVariable int id, @RequestBody Map<String, Boolean> request) {
        Boolean accept = request.get("accept");
        
        if (accept == null) {
            return ResponseEntity.badRequest().body("参数不能为空");
        }
        
        boolean success = userService.updateAcceptTemporaryChat(id, accept);
        if (success) {
            return ResponseEntity.ok("设置更新成功");
        }
        return ResponseEntity.badRequest().body("设置更新失败");
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable int id) {
        boolean success = userService.deleteUser(id);
        if (success) {
            return ResponseEntity.ok("用户删除成功");
        }
        return ResponseEntity.badRequest().body("用户删除失败");
    }
    
    @GetMapping("/{id}/friends")
    public ResponseEntity<List<Map<String, Object>>> getUserFriends(@PathVariable int id) {
        return ResponseEntity.ok(userService.getUserFriends(id));
    }
    
    @GetMapping("/{id}/rooms")
    public ResponseEntity<List<Map<String, Object>>> getUserRooms(@PathVariable int id) {
        return ResponseEntity.ok(userService.getUserRooms(id));
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<String> updateUserStatus(@PathVariable int id, @RequestBody Map<String, String> request) {
        String status = request.get("status");
        
        if (status == null) {
            return ResponseEntity.badRequest().body("状态不能为空");
        }
        
        boolean success = userService.updateUserStatus(id, status);
        if (success) {
            return ResponseEntity.ok("状态更新成功");
        }
        return ResponseEntity.badRequest().body("状态更新失败");
    }
    
    @GetMapping("/status/stats")
    public ResponseEntity<Map<String, Object>> getUserStatusStats() {
        return ResponseEntity.ok(userService.getUserStatusStats());
    }
}
