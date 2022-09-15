package com.example.parameterstore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

@RestController
public class GreetingController {

    private final SsmClient ssmClient;

    public GreetingController(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    @GetMapping("/greetings")
    public String greeting() {
        GetParameterResponse parameter = this.ssmClient.getParameter(GetParameterRequest.builder()
                        .name("/spring/config/text")
                .build());
        return parameter.parameter().value();
    }
}
