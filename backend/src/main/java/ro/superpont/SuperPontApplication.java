package ro.superpont;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SuperPontApplication {

    public static void main(String[] args) {
        SpringApplication.run(SuperPontApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

