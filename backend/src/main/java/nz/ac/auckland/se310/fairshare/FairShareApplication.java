package nz.ac.auckland.se310.fairshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FairShareApplication {

  public static void main(String[] args) {
    SpringApplication.run(FairShareApplication.class, args);
  }
}
