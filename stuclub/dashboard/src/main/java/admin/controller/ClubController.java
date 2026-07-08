package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import admin.service.ClubService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/clubs")
public class ClubController {
    
    @Autowired
    private ClubService clubService;
    
    @GetMapping
    public String clubs(Model model) {
        model.addAttribute("clubs", clubService.getAllClubs());
        return "clubs";
    }
    
    @GetMapping("/{id}")
    public String clubDetail(@PathVariable int id, Model model) {
        Map<String, Object> club = clubService.getClubById(id);
        if (club == null) {
            return "redirect:/clubs";
        }
        model.addAttribute("club", club);
        model.addAttribute("members", clubService.getClubMembers(id));
        return "club-detail";
    }
    
    @GetMapping("/api")
    public ResponseEntity<List<Map<String, Object>>> getAllClubsApi() {
        return ResponseEntity.ok(clubService.getAllClubs());
    }
    
    @GetMapping("/api/{id}")
    public ResponseEntity<Map<String, Object>> getClubByIdApi(@PathVariable int id) {
        Map<String, Object> club = clubService.getClubById(id);
        if (club != null) {
            return ResponseEntity.ok(club);
        }
        return ResponseEntity.notFound().build();
    }
    
    @GetMapping("/api/search")
    public ResponseEntity<List<Map<String, Object>>> searchClubs(@RequestParam String term) {
        return ResponseEntity.ok(clubService.searchClubs(term));
    }
    
    @PostMapping("/api")
    public ResponseEntity<String> createClub(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String category = (String) request.get("category");
        String description = (String) request.get("description");
        String advisor = (String) request.get("advisor");
        Integer maxMembers = (Integer) request.get("maxMembers");
        Integer founderId = (Integer) request.get("founderId");
        
        if (name == null || category == null) {
            return ResponseEntity.badRequest().body("社团名称和类别不能为空");
        }
        
        boolean success = clubService.createClub(name, category, description, advisor, 
                                                 maxMembers != null ? maxMembers : 100, 
                                                 founderId != null ? founderId : 1);
        if (success) {
            return ResponseEntity.ok("社团创建成功");
        }
        return ResponseEntity.badRequest().body("社团创建失败");
    }
    
    @PutMapping("/api/{id}")
    public ResponseEntity<String> updateClub(@PathVariable int id, @RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String category = (String) request.get("category");
        String description = (String) request.get("description");
        String advisor = (String) request.get("advisor");
        Integer maxMembers = (Integer) request.get("maxMembers");
        String status = (String) request.get("status");
        
        if (name == null || category == null) {
            return ResponseEntity.badRequest().body("社团名称和类别不能为空");
        }
        
        boolean success = clubService.updateClub(id, name, category, description, advisor, 
                                                 maxMembers != null ? maxMembers : 100, status);
        if (success) {
            return ResponseEntity.ok("社团更新成功");
        }
        return ResponseEntity.badRequest().body("社团更新失败");
    }
    
    @DeleteMapping("/api/{id}")
    public ResponseEntity<String> deleteClub(@PathVariable int id) {
        boolean success = clubService.deleteClub(id);
        if (success) {
            return ResponseEntity.ok("社团删除成功");
        }
        return ResponseEntity.badRequest().body("社团删除失败");
    }
    
    @PostMapping("/api/{id}/approve")
    public ResponseEntity<String> approveClub(@PathVariable int id) {
        boolean success = clubService.approveClub(id);
        if (success) {
            return ResponseEntity.ok("社团审核通过");
        }
        return ResponseEntity.badRequest().body("审核失败");
    }
    
    @PostMapping("/api/{id}/reject")
    public ResponseEntity<String> rejectClub(@PathVariable int id) {
        boolean success = clubService.rejectClub(id);
        if (success) {
            return ResponseEntity.ok("社团审核拒绝");
        }
        return ResponseEntity.badRequest().body("拒绝失败");
    }
    
    @GetMapping("/api/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getClubMembers(@PathVariable int id) {
        return ResponseEntity.ok(clubService.getClubMembers(id));
    }
    
    @PostMapping("/api/{id}/apply")
    public ResponseEntity<String> applyJoin(@PathVariable int id, @RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        String reason = (String) request.get("reason");
        
        if (username == null) {
            return ResponseEntity.badRequest().body("用户名不能为空");
        }
        
        boolean success = clubService.applyJoinClubByUsername(id, username, reason);
        if (success) {
            return ResponseEntity.ok("入社申请已提交，请等待审核");
        }
        return ResponseEntity.badRequest().body("申请提交失败，您可能已经是该社团成员或已有待审核的申请");
    }
    
    @PostMapping("/api/{id}/members")
    public ResponseEntity<String> addMember(@PathVariable int id, @RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        String role = (String) request.get("role");
        
        if (userId == null || role == null) {
            return ResponseEntity.badRequest().body("用户ID和角色不能为空");
        }
        
        boolean success = clubService.addMemberToClub(id, userId, role);
        if (success) {
            return ResponseEntity.ok("成员添加成功");
        }
        return ResponseEntity.badRequest().body("成员添加失败");
    }
    
    @PostMapping("/api/{id}/members/{userId}/approve")
    public ResponseEntity<String> approveMember(@PathVariable int id, @PathVariable int userId) {
        boolean success = clubService.approveMember(id, userId);
        if (success) {
            return ResponseEntity.ok("成员审核通过");
        }
        return ResponseEntity.badRequest().body("审核失败");
    }
    
    @DeleteMapping("/api/{id}/members/{userId}")
    public ResponseEntity<String> removeMember(@PathVariable int id, @PathVariable int userId) {
        boolean success = clubService.removeMemberFromClub(id, userId);
        if (success) {
            return ResponseEntity.ok("成员移除成功");
        }
        return ResponseEntity.badRequest().body("成员移除失败");
    }
    
    @PutMapping("/api/{id}/members/{userId}")
    public ResponseEntity<String> updateMemberRole(@PathVariable int id, @PathVariable int userId, 
                                                   @RequestBody Map<String, String> request) {
        String newRole = request.get("role");
        
        if (newRole == null) {
            return ResponseEntity.badRequest().body("角色不能为空");
        }
        
        boolean success = clubService.updateMemberRole(id, userId, newRole);
        if (success) {
            return ResponseEntity.ok("角色更新成功");
        }
        return ResponseEntity.badRequest().body("角色更新失败");
    }
}