package com.arenax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class ArenaXApplication {
    public static void main(String[] args) { SpringApplication.run(ArenaXApplication.class, args); }
    @Bean WebMvcConfigurer apiCors() {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("*");
            }
        };
    }
}
