package admin.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import admin.service.UserService;
import admin.service.RoomService;
import admin.service.MessageService;

@Controller
public class MainController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RoomService roomService;
    
    @Autowired
    private MessageService messageService;
    
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("userCount", userService.getUserCount());
        model.addAttribute("roomCount", roomService.getRoomCount());
        model.addAttribute("messageCount", messageService.getMessageCount());
        model.addAttribute("userStatusStats", userService.getUserStatusStats());
        return "index";
    }
    
    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.getAllUsers());
        return "users";
    }
    
    @GetMapping("/rooms")
    public String rooms(Model model) {
        model.addAttribute("rooms", roomService.getAllRooms());
        return "rooms";
    }
    
    @GetMapping("/messages")
    public String messages(Model model) {
        model.addAttribute("messages", messageService.getAllMessages(50, 0));
        model.addAttribute("stats", messageService.getMessageStats());
        return "messages";
    }
}
