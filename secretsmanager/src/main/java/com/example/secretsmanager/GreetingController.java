package com.example.secretsmanager;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

@RestController
public class GreetingController {

    private final SecretsManagerClient secretsManagerClient;

    public GreetingController(SecretsManagerClient secretsManagerClient) {
        this.secretsManagerClient = secretsManagerClient;
    }

    @GetMapping("/greetings")
    public String greeting() {
        GetSecretValueResponse secretValue = this.secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
                .secretId("/spring/secret/text")
                .build());
        return secretValue.secretString();
    }
}
