package com.wastelens.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {
        "/login",
        "/dashboard",
        "/entries",
        "/entries/new",
        "/compare"
    })
    public String forwardSpa() {
        return "forward:/index.html";
    }
}
