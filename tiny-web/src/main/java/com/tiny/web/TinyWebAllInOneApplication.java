package com.tiny.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
@SpringBootApplication(
		exclude = { ErrorMvcAutoConfiguration.class }
)
public class TinyWebAllInOneApplication {

	public static void main(String[] args) {
		SpringApplication.run(TinyWebAllInOneApplication.class, args);
	}

}
