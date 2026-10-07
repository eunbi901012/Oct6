package kr.ac.knue.common;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots the blocking HTTP/MyBatis backend with database-managed migrations and persisted security. */
@SpringBootApplication
@MapperScan("kr.ac.knue.common.adapter.mybatis")
public class CommonApplication {
    /** Starts the application using externally supplied environment configuration. */
    public static void main(String[] args) {
        SpringApplication.run(CommonApplication.class, args);
    }
}
