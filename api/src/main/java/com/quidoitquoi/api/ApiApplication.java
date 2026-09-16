package com.quidoitquoi.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class ApiApplication {

	private static final Logger logger = LoggerFactory.getLogger(ApiApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

	@Bean
	public InitializingBean verifyDatabaseHealth(JdbcTemplate jdbcTemplate) {
		return () -> {
			Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			if (!Integer.valueOf(1).equals(result)) {
				throw new IllegalStateException("Database health check returned an unexpected result: " + result);
			}
			logger.info("Database health check passed");
		};
	}

}
