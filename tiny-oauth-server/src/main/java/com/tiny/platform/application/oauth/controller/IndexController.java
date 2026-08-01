package com.tiny.platform.application.oauth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class IndexController {
    @GetMapping("/")
    public ResponseEntity<?> Index() {
        return ResponseEntity.ok().build();
    }
}
