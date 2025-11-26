package com.kryptforge.clusterforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:testdb-app;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class ClusterforgeApplicationTests {

	@Test
	void contextLoads() {
	}

}
