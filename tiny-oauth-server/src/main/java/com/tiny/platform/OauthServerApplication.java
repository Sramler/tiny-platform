package com.tiny.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableAsync
@EnableScheduling
@SpringBootApplication(
    scanBasePackages = {"com.tiny.platform"},
    exclude = {
        // 排除 Redis 自动配置，让 Redis 变为可选
        // 只有在明确配置 tiny.idempotent.store=redis 时才会通过条件配置启用
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
    }
)
@EntityScan(basePackages = {"com.tiny.platform"})
@EnableJpaRepositories(basePackages = {"com.tiny.platform"})
public class OauthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OauthServerApplication.class, args);
    }

}
