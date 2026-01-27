package com.example.demo;

import com.example.demo.core.GilCodedException;
import com.example.demo.core.GilErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(DemoApplication.class, args);
  }

  @Bean
  CommandLineRunner printSecrets(
      @Value("${db.username:NOT_FOUND}") String dbUser,
      @Value("${db.password:NOT_FOUND}") String dbPass,
      @Value("${ssh.host:NOT_FOUND}") String sshHost,
      @Value("${ssh.port:NOT_FOUND}") String sshPort
  ) {

    // exemple d'usage d'exception:

    if (Math.random() < 1) {
      throw new GilCodedException(GilErrorCode.CORE_CONFIG_INVALID, "Missing field: flowId");
    }
    return args -> {
      System.out.println("=== Values read from Vault ===");
      System.out.println("db.username = " + dbUser);
      System.out.println("db.password = " + dbPass);
      System.out.println("ssh.host    = " + sshHost);
      System.out.println("ssh.port    = " + sshPort);
      System.out.println("==============================");
    };
  }
}