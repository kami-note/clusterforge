package com.seveninterprise.clusterforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClusterforgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClusterforgeApplication.class, args);
	}

}
