package org.learn.testRepositoryService;

import org.learn.autoconfig.CommonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CommonAutoConfiguration.class)
public class TestRepositoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestRepositoryServiceApplication.class, args);
    }

}
