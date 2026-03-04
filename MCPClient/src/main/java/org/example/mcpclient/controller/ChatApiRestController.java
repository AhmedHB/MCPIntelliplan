package org.example.mcpclient.controller;

import org.example.mcpclient.dto.ChatRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api")
public interface ChatApiRestController {

    @PostMapping("/chat")
    ResponseEntity<String> chat(@RequestBody ChatRequest request);
}
