package com.assesswise.processdesigner;

import com.assesswise.processdesigner.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ProcessDesignerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessDesignerApplication.class, args);
    }
}
