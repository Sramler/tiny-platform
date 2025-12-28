package com.tiny.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TinyOauthServerApplicationTests {

    @Test
    void applicationClassLoadsWithoutSpringContext() {
        OauthServerApplication application = new OauthServerApplication();
        assertNotNull(application);
    }
}
