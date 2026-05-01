package com.booknest.adminserver;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Main entry point for the BookNest Admin Server.
 * 
 * This service leverages 'de.codecentric:spring-boot-admin-server' to provide
 * a comprehensive administrative UI for monitoring and managing all
 * microservices within the BookNest ecosystem.
 * 
 * Key Roles:
 * 1. Monitoring: Visualizes health metrics, JVM stats, and logs for all active
 * services.
 * 2. Service Discovery: Integrates with Eureka to automatically detect new
 * service instances.
 * 3. Management: Allows administrators to change log levels or trigger thread
 * dumps in real-time.
 */
@SpringBootApplication
@EnableAdminServer
@EnableDiscoveryClient
public class AdminServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdminServerApplication.class, args);
	}

}
