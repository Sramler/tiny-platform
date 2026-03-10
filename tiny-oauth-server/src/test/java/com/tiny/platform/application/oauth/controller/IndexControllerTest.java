package com.tiny.platform.application.oauth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexControllerTest {

    @Test
    void index_shouldReturn200() {
        IndexController controller = new IndexController();
        assertEquals(HttpStatus.OK, controller.Index().getStatusCode());
    }
}

