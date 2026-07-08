package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import admin.service.ActivityService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityApiController {
    
    @Autowired
    private ActivityService activityService;
    
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllActivities() {
        return ResponseEntity.ok(activityService.getAllActivities());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getActivityById(@PathVariable int id) {
        Map<String, Object> activity = activityService.getActivityById(id);
        if (activity != null) {
            return ResponseEntity.ok(activity);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchActivities(@RequestParam String term) {
        return ResponseEntity.ok(activityService.searchActivities(term));
    }
    
    @PostMapping
    public ResponseEntity<String> createActivity(@RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String activityType = (String) request.get("activityType");
        String startTime = (String) request.get("startTime");
        String endTime = (String) request.get("endTime");
        String location = (String) request.get("location");
        Integer maxParticipants = (Integer) request.get("maxParticipants");
        String registrationDeadline = (String) request.get("registrationDeadline");
        Double budget = (Double) request.get("budget");
        Integer clubId = (Integer) request.get("clubId");
        Integer createdBy = (Integer) request.get("createdBy");
        
        if (title == null || activityType == null || startTime == null || endTime == null) {
            return ResponseEntity.badRequest().body("标题、类型、开始时间和结束时间不能为空");
        }
        
        boolean success = activityService.createActivity(title, description, activityType,
                                                        startTime, endTime, location,
                                                        maxParticipants != null ? maxParticipants : 0,
                                                        registrationDeadline,
                                                        budget != null ? budget : 0,
                                                        clubId != null ? clubId : 1,
                                                        createdBy != null ? createdBy : 1);
        if (success) {
            return ResponseEntity.ok("活动创建成功");
        }
        return ResponseEntity.badRequest().body("活动创建失败");
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<String> updateActivity(@PathVariable int id, @RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String activityType = (String) request.get("activityType");
        String startTime = (String) request.get("startTime");
        String endTime = (String) request.get("endTime");
        String location = (String) request.get("location");
        Integer maxParticipants = (Integer) request.get("maxParticipants");
        String registrationDeadline = (String) request.get("registrationDeadline");
        Double budget = (Double) request.get("budget");
        String status = (String) request.get("status");
        
        if (title == null || activityType == null) {
            return ResponseEntity.badRequest().body("标题和类型不能为空");
        }
        
        boolean success = activityService.updateActivity(id, title, description, activityType,
                                                        startTime, endTime, location,
                                                        maxParticipants != null ? maxParticipants : 0,
                                                        registrationDeadline,
                                                        budget != null ? budget : 0,
                                                        status);
        if (success) {
            return ResponseEntity.ok("活动更新成功");
        }
        return ResponseEntity.badRequest().body("活动更新失败");
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteActivity(@PathVariable int id) {
        boolean success = activityService.deleteActivity(id);
        if (success) {
            return ResponseEntity.ok("活动删除成功");
        }
        return ResponseEntity.badRequest().body("活动删除失败");
    }
    
    @PostMapping("/{id}/publish")
    public ResponseEntity<String> publishActivity(@PathVariable int id) {
        boolean success = activityService.publishActivity(id);
        if (success) {
            return ResponseEntity.ok("活动已发布");
        }
        return ResponseEntity.badRequest().body("发布失败");
    }
    
    @PostMapping("/{id}/complete")
    public ResponseEntity<String> completeActivity(@PathVariable int id) {
        boolean success = activityService.completeActivity(id);
        if (success) {
            return ResponseEntity.ok("活动已完成");
        }
        return ResponseEntity.badRequest().body("操作失败");
    }
    
    @GetMapping("/{id}/registrations")
    public ResponseEntity<List<Map<String, Object>>> getRegistrations(@PathVariable int id) {
        return ResponseEntity.ok(activityService.getActivityRegistrations(id));
    }
    
    @PostMapping("/{id}/registrations")
    public ResponseEntity<String> addRegistration(@PathVariable int id, @RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        
        if (userId == null) {
            return ResponseEntity.badRequest().body("用户ID不能为空");
        }
        
        boolean success = activityService.addRegistration(id, userId);
        if (success) {
            return ResponseEntity.ok("报名成功");
        }
        return ResponseEntity.badRequest().body("报名失败");
    }
    
    @PostMapping("/registrations/{registrationId}/approve")
    public ResponseEntity<String> approveRegistration(@PathVariable int registrationId) {
        boolean success = activityService.approveRegistration(registrationId);
        if (success) {
            return ResponseEntity.ok("报名审核通过");
        }
        return ResponseEntity.badRequest().body("审核失败");
    }
    
    @PostMapping("/registrations/{registrationId}/reject")
    public ResponseEntity<String> rejectRegistration(@PathVariable int registrationId) {
        boolean success = activityService.rejectRegistration(registrationId);
        if (success) {
            return ResponseEntity.ok("报名已拒绝");
        }
        return ResponseEntity.badRequest().body("操作失败");
    }
}