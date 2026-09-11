package io.myjobai.launcher;

import io.myjobai.runtime.RuntimeConfiguration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RuntimeConfiguration.class)
public class AiWorkerApplication {
  public static void main(String[] args) {
    var app = new SpringApplication(AiWorkerApplication.class);
    app.setDefaultProperties(Map.of("APP_ROLE", "ai"));
    app.run(args);
  }
}
