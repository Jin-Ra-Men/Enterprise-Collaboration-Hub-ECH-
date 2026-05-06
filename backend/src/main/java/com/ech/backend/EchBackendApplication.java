package com.ech.backend;

import com.ech.backend.api.aigateway.AiGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiGatewayProperties.class)
public class EchBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchBackendApplication.class, args);
    }
}
