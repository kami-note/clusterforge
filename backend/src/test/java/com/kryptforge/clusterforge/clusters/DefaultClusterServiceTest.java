package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateFileEntry;

class DefaultClusterServiceTest {

	private ClusterRepository repository;
	private TemplateService templateService;
	private DefaultClusterService service;

	@BeforeEach
	void setup() {
		repository = mock(ClusterRepository.class);
		templateService = mock(TemplateService.class);
		service = new DefaultClusterService(repository, templateService);
	}

	@Test
	void create_validatesNameUniquenessAndTemplate() throws Exception {
		when(repository.existsByName("c1")).thenReturn(false);
		when(templateService.getTemplate("webserver-php"))
			.thenReturn(new TemplateDetail("webserver-php", "webserver-php", true, List.of(new TemplateFileEntry("a", 1)), null));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var params = new ClusterService.ClusterParams(Map.of("A","1"), List.of(8080), List.of("./vol:/data"));
		ClusterInstance created = service.create("c1", "webserver-php", params);
		assertEquals("c1", created.getName());
		assertEquals("webserver-php", created.getTemplateName());
		assertEquals(ClusterStatus.PENDING, created.getStatus());
		assertEquals(Map.of("A","1"), created.getEnv());
		assertEquals(List.of(8080), created.getPorts());
	}

	@Test
	void updateStatus_and_updateParams() {
		ClusterInstance existing = new ClusterInstance();
		existing.setName("c2");
		existing.setTemplateName("webserver-php");
		existing.setStatus(ClusterStatus.PENDING);

		when(repository.findById(any())).thenReturn(Optional.of(existing));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var updatedStatus = service.updateStatus(java.util.UUID.randomUUID(), ClusterStatus.ACTIVE);
		assertEquals(ClusterStatus.ACTIVE, updatedStatus.getStatus());

		var updatedParams = service.updateParams(java.util.UUID.randomUUID(), new ClusterService.ClusterParams(Map.of("B","2"), List.of(80,80,443), List.of("/v:/v")));
		assertEquals(Map.of("B","2"), updatedParams.getEnv());
		assertEquals(List.of(80,443), updatedParams.getPorts());
	}
}


