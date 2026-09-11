package io.myjobai.launcher;

import io.myjobai.runtime.RuntimeConfiguration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RuntimeConfiguration.class)
public class CommunicationApplication {
  public static void main(String[] args) {
    var app = new SpringApplication(CommunicationApplication.class);
    app.setDefaultProperties(Map.of("APP_ROLE", "communication"));
    app.run(args);
  }
}
