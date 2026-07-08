package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import admin.service.AnnouncementService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    
    @Autowired
    private AnnouncementService announcementService;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllAnnouncementsApi() {
        return ResponseEntity.ok(announcementService.getAllAnnouncements());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAnnouncementByIdApi(@PathVariable int id) {
        Map<String, Object> announcement = announcementService.getAnnouncementById(id);
        if (announcement != null) {
            return ResponseEntity.ok(announcement);
        }
        return ResponseEntity.notFound().build();
    }
    
    @PostMapping
    public ResponseEntity<String> createAnnouncement(@RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        String priority = (String) request.get("priority");
        Integer isPinned = (Integer) request.get("isPinned");
        Integer clubId = (Integer) request.get("clubId");
        Integer createdBy = (Integer) request.get("createdBy");
        
        if (title == null || content == null) {
            return ResponseEntity.badRequest().body("标题和内容不能为空");
        }
        
        boolean success = announcementService.createAnnouncement(
            title, content, priority != null ? priority : "NORMAL",
            isPinned != null && isPinned == 1 ? 1 : 0,
            clubId != null ? clubId : 0,
            createdBy != null ? createdBy : 1
        );
        
        if (success) {
            return ResponseEntity.ok("公告创建成功");
        }
        return ResponseEntity.badRequest().body("公告创建失败");
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<String> updateAnnouncement(@PathVariable int id, @RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        String priority = (String) request.get("priority");
        Integer isPinned = (Integer) request.get("isPinned");
        String status = (String) request.get("status");
        Integer clubId = (Integer) request.get("clubId");
        
        if (title == null || content == null) {
            return ResponseEntity.badRequest().body("标题和内容不能为空");
        }
        
        boolean success = announcementService.updateAnnouncement(
            id, title, content, priority != null ? priority : "NORMAL",
            isPinned != null && isPinned == 1 ? 1 : 0,
            status != null ? status : "DRAFT",
            clubId != null ? clubId : 0
        );
        
        if (success) {
            return ResponseEntity.ok("公告更新成功");
        }
        return ResponseEntity.badRequest().body("公告更新失败");
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAnnouncement(@PathVariable int id) {
        boolean success = announcementService.deleteAnnouncement(id);
        if (success) {
            return ResponseEntity.ok("公告删除成功");
        }
        return ResponseEntity.badRequest().body("公告删除失败");
    }
    
    @PostMapping("/{id}/publish")
    public ResponseEntity<String> publishAnnouncement(@PathVariable int id) {
        boolean success = announcementService.publishAnnouncement(id);
        if (success) {
            return ResponseEntity.ok("公告已发布");
        }
        return ResponseEntity.badRequest().body("发布失败");
    }
}
