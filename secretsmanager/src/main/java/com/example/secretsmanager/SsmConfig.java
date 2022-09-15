package com.example.secretsmanager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

@Configuration
public class SsmConfig {

    @Value("${spring.cloud.aws.secretsmanager.endpoint}")
    private URI secretsManagerClientEndpoint;

    private final AwsCredentialsProvider awsCredentialsProvider;

    private final AwsRegionProvider awsRegionProvider;

    public SsmConfig(AwsCredentialsProvider awsCredentialsProvider, AwsRegionProvider awsRegionProvider) {
        this.awsCredentialsProvider = awsCredentialsProvider;
        this.awsRegionProvider = awsRegionProvider;
    }

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        SecretsManagerClient secretsManagerClient = SecretsManagerClient.builder()
                .credentialsProvider(this.awsCredentialsProvider)
                .endpointOverride(this.secretsManagerClientEndpoint)
                .region(this.awsRegionProvider.getRegion())
                .build();
        return secretsManagerClient;
    }

}
