package com.paynest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PayNestApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayNestApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PayNestApplication.class, args);
        LOGGER.info("event=application outcome=started");
    }
}
