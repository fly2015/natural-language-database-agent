package com.nlda.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatPageController {

    @GetMapping("/")
    public String chat() {
        return "chat";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
