package org.learn.testExecutionService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.learn.autoconfig.CommonAutoConfiguration;

@SpringBootApplication
@Import(CommonAutoConfiguration.class)
public class TestExecutionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestExecutionServiceApplication.class, args);
    }

}
