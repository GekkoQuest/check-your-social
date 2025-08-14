package quest.gekko.cys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CheckYourSocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckYourSocialApplication.class, args);
    }

}
