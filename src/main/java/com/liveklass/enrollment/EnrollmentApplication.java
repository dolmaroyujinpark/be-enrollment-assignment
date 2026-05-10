package com.liveklass.enrollment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EnrollmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnrollmentApplication.class, args);
    }
}
