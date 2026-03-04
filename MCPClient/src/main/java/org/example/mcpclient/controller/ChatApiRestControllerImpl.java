package org.example.mcpclient.controller;

import org.example.mcpclient.dto.ChatRequest;
import org.example.mcpclient.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatApiRestControllerImpl implements ChatApiRestController {

    private final ChatService chatService;

    public ChatApiRestControllerImpl(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public ResponseEntity<String> chat(ChatRequest request) {
        return ResponseEntity.ok().body(chatService.chat(request));
    }
}
