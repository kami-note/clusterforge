package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ClusterRepositoryTest {

	@Autowired
	private ClusterRepository repository;

	@Test
	void saveAndFindByName_uniqueName() {
		ClusterInstance c = new ClusterInstance();
		c.setName("my-cluster");
		c.setTemplateName("webserver-php");
		c.setStatus(ClusterStatus.PENDING);
		repository.save(c);

		Optional<ClusterInstance> found = repository.findByName("my-cluster");
		assertTrue(found.isPresent());
		assertEquals("webserver-php", found.get().getTemplateName());
		assertTrue(repository.existsByName("my-cluster"));
	}
}


