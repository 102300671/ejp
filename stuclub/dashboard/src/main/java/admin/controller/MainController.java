package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import admin.service.UserService;
import admin.service.ClubService;
import admin.service.ActivityService;
import admin.service.MessageService;

@Controller
public class MainController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ClubService clubService;
    
    @Autowired
    private ActivityService activityService;
    
    @Autowired
    private MessageService messageService;
    
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("userCount", userService.getUserCount());
        model.addAttribute("clubCount", clubService.getClubCount());
        model.addAttribute("activityCount", activityService.getActivityCount());
        model.addAttribute("pendingClubCount", clubService.getPendingClubCount());
        model.addAttribute("pendingClubs", clubService.getPendingClubs());
        model.addAttribute("recentActivities", activityService.getRecentActivities(5));
        return "index";
    }
    
    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "users";
    }
    
    @GetMapping("/messages")
    public String messages(Model model) {
        model.addAttribute("messages", messageService.getAllMessages(50, 0));
        model.addAttribute("stats", messageService.getMessageStats());
        return "messages";
    }
    
    @GetMapping("/announcements")
    public String announcements() {
        return "announcements";
    }
}