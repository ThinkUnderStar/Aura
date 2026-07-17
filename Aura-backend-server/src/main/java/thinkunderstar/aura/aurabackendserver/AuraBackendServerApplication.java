package thinkunderstar.aura.aurabackendserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuraBackendServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuraBackendServerApplication.class, args);
    }

}
