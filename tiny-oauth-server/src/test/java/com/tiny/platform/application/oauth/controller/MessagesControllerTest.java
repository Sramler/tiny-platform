package com.tiny.platform.application.oauth.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesControllerTest {

    @Test
    void messages_shouldReturnExpectedStrings() {
        MessagesController controller = new MessagesController();
        assertEquals(" hello Message 1", controller.getMessages1());
        assertEquals(" hello Message 2", controller.getMessages2());
        assertEquals(" hello Message 3", controller.getMessages3());
    }
}

