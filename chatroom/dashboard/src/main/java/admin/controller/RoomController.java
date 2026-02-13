package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import admin.service.RoomService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    
    @Autowired
    private RoomService roomService;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRoomById(@PathVariable int id) {
        Map<String, Object> room = roomService.getRoomById(id);
        if (room != null) {
            return ResponseEntity.ok(room);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchRooms(@RequestParam String term) {
        return ResponseEntity.ok(roomService.searchRooms(term));
    }
    
    @PostMapping
    public ResponseEntity<String> createRoom(@RequestBody Map<String, String> request) {
        String roomName = request.get("roomName");
        String roomType = request.get("roomType");
        
        if (roomName == null || roomType == null) {
            return ResponseEntity.badRequest().body("房间名称和类型不能为空");
        }
        
        boolean success = roomService.createRoom(roomName, roomType);
        if (success) {
            return ResponseEntity.ok("房间创建成功");
        }
        return ResponseEntity.badRequest().body("房间创建失败");
    }
    
    @PutMapping("/{id}/name")
    public ResponseEntity<String> updateRoomName(@PathVariable int id, @RequestBody Map<String, String> request) {
        String newName = request.get("name");
        
        if (newName == null) {
            return ResponseEntity.badRequest().body("房间名称不能为空");
        }
        
        boolean success = roomService.updateRoomName(id, newName);
        if (success) {
            return ResponseEntity.ok("房间名称更新成功");
        }
        return ResponseEntity.badRequest().body("房间名称更新失败");
    }
    
    @PutMapping("/{id}/type")
    public ResponseEntity<String> updateRoomType(@PathVariable int id, @RequestBody Map<String, String> request) {
        String roomType = request.get("roomType");
        
        if (roomType == null) {
            return ResponseEntity.badRequest().body("房间类型不能为空");
        }
        
        boolean success = roomService.updateRoomType(id, roomType);
        if (success) {
            return ResponseEntity.ok("房间类型更新成功");
        }
        return ResponseEntity.badRequest().body("房间类型更新失败");
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRoom(@PathVariable int id) {
        boolean success = roomService.deleteRoom(id);
        if (success) {
            return ResponseEntity.ok("房间删除成功");
        }
        return ResponseEntity.badRequest().body("房间删除失败");
    }
    
    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getRoomMembers(@PathVariable int id) {
        return ResponseEntity.ok(roomService.getRoomMembers(id));
    }
    
    @PostMapping("/{id}/members")
    public ResponseEntity<String> addMember(@PathVariable int id, @RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        String role = (String) request.get("role");
        
        if (userId == null || role == null) {
            return ResponseEntity.badRequest().body("用户ID和角色不能为空");
        }
        
        boolean success = roomService.addUserToRoom(id, userId, role);
        if (success) {
            return ResponseEntity.ok("成员添加成功");
        }
        return ResponseEntity.badRequest().body("成员添加失败");
    }
    
    @PutMapping("/{id}/members/{userId}")
    public ResponseEntity<String> updateMemberRole(@PathVariable int id, @PathVariable int userId, @RequestBody Map<String, String> request) {
        String newRole = request.get("role");
        
        if (newRole == null) {
            return ResponseEntity.badRequest().body("角色不能为空");
        }
        
        boolean success = roomService.updateUserRole(id, userId, newRole);
        if (success) {
            return ResponseEntity.ok("角色更新成功");
        }
        return ResponseEntity.badRequest().body("角色更新失败");
    }
    
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<String> removeMember(@PathVariable int id, @PathVariable int userId) {
        boolean success = roomService.removeUserFromRoom(id, userId);
        if (success) {
            return ResponseEntity.ok("成员移除成功");
        }
        return ResponseEntity.badRequest().body("成员移除失败");
    }
}
