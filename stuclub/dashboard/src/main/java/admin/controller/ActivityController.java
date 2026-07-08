package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import admin.service.ActivityService;

import java.util.Map;

@Controller
public class ActivityController {
    
    @Autowired
    private ActivityService activityService;
    
    @GetMapping("/activities")
    public String activities(Model model) {
        model.addAttribute("activities", activityService.getAllActivities());
        return "activities";
    }
    
    @GetMapping("/activities/{id}")
    public String activityDetail(@PathVariable int id, Model model) {
        Map<String, Object> activity = activityService.getActivityById(id);
        if (activity == null) {
            return "redirect:/activities";
        }
        model.addAttribute("activity", activity);
        model.addAttribute("registrations", activityService.getActivityRegistrations(id));
        return "activity-detail";
    }
}