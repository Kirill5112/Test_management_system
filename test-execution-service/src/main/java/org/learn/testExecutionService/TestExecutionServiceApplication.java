package org.learn.testExecutionService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.learn.autoconfig.CommonAutoConfiguration;

@SpringBootApplication
@EnableFeignClients(basePackages = "org.learn.testExecutionService.client")
@Import(CommonAutoConfiguration.class)
public class TestExecutionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestExecutionServiceApplication.class, args);
    }

}
