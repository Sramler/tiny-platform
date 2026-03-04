package com.tiny.platform.application.oauth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthControllerCoverageTest {

    @Test
    void shouldReturnOkFromIndexController() {
        IndexController controller = new IndexController();

        ResponseEntity<?> response = controller.Index();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReturnMessageStrings() {
        MessagesController controller = new MessagesController();

        assertThat(controller.getMessages1()).isEqualTo(" hello Message 1");
        assertThat(controller.getMessages2()).isEqualTo(" hello Message 2");
        assertThat(controller.getMessages3()).isEqualTo(" hello Message 3");
    }
}
