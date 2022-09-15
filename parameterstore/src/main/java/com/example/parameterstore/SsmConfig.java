package com.example.parameterstore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

@Configuration
public class SsmConfig {

    @Value("${spring.cloud.aws.parameterstore.endpoint}")
    private URI ssmClientEndpoint;

    private final AwsCredentialsProvider awsCredentialsProvider;

    private final AwsRegionProvider awsRegionProvider;

    public SsmConfig(AwsCredentialsProvider awsCredentialsProvider, AwsRegionProvider awsRegionProvider) {
        this.awsCredentialsProvider = awsCredentialsProvider;
        this.awsRegionProvider = awsRegionProvider;
    }

    @Bean
    public SsmClient ssmClient() {
        SsmClient ssmClient = SsmClient.builder()
                .credentialsProvider(this.awsCredentialsProvider)
                .endpointOverride(this.ssmClientEndpoint)
                .region(this.awsRegionProvider.getRegion())
                .build();
        return ssmClient;
    }

}
