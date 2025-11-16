package com.kryptforge.clusterforge.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.github.dockerjava.api.model.Container;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.TemplateInstantiationService;
import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.templates.InstantiationResult;
import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateFileEntry;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateRequest;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateResponse;
import com.kryptforge.clusterforge.templates.dto.TemplateSummary;

class TemplateControllerTest {

	private TemplateService templateService;
	private TemplateInstantiationService instantiationService;
	private DockerEngineService dockerEngineService;
	private ClusterService clusterService;
	private TemplateController controller;

	@BeforeEach
	void setup() {
		templateService = mock(TemplateService.class);
		instantiationService = mock(TemplateInstantiationService.class);
		dockerEngineService = mock(DockerEngineService.class);
		clusterService = mock(ClusterService.class);
		controller = new TemplateController(templateService, instantiationService, dockerEngineService, clusterService);
	}

	@Test
	void listTemplates_returnsList() throws Exception {
		List<TemplateSummary> summaries = List.of(
			new TemplateSummary("template1", "template1", true, java.time.Instant.now())
		);
		when(templateService.listTemplates()).thenReturn(summaries);

		List<TemplateSummary> result = controller.listTemplates();

		assertEquals(1, result.size());
		assertEquals("template1", result.get(0).name());
	}

	@Test
	void listTemplates_throws500OnIOException() throws Exception {
		when(templateService.listTemplates()).thenThrow(new IOException("IO error"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.listTemplates();
		});

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
	}

	@Test
	void getTemplate_returnsDetail() throws Exception {
		TemplateDetail detail = new TemplateDetail(
			"template1",
			"template1",
			true,
			List.of(new TemplateFileEntry("file.txt", 100)),
			null
		);
		when(templateService.getTemplate("template1")).thenReturn(detail);

		TemplateDetail result = controller.getTemplate("template1");

		assertEquals("template1", result.name());
		assertTrue(result.composePresent());
	}

	@Test
	void getTemplate_throws404OnNotFound() throws Exception {
		when(templateService.getTemplate("not-found")).thenThrow(new NoSuchFileException("not found"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTemplate("not-found");
		});

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
	}

	@Test
	void getTemplate_throws400OnInvalidArgument() throws Exception {
		when(templateService.getTemplate("invalid")).thenThrow(new IllegalArgumentException("invalid"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.getTemplate("invalid");
		});

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

	@Test
	void instantiate_returns201OnSuccess() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			"my-instance",
			Map.of("VAR", "value"),
			List.of("8080:80"),
			List.of("/host:/container")
		);

		// simula container não existente
		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		when(instantiationService.instantiate(
			eq("template1"),
			eq("my-instance"),
			any(),
			any(),
			any()
		)).thenReturn(new InstantiationResult("container-id-123", List.of("9000:80")));
		
		// Mock do ClusterService
		com.kryptforge.clusterforge.clusters.ClusterInstance mockInstance = new com.kryptforge.clusterforge.clusters.ClusterInstance();
		mockInstance.setName("my-instance");
		when(clusterService.create(anyString(), anyString(), any())).thenReturn(mockInstance);
		when(clusterService.updateContainerId(any(), anyString())).thenReturn(mockInstance);
		when(clusterService.updateStatus(any(), any())).thenReturn(mockInstance);

		ResponseEntity<TemplateInstantiateResponse> response = controller.instantiate("template1", request);

		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("container-id-123", response.getBody().containerId());
		assertEquals("my-instance", response.getBody().name());
	}

	@Test
	void instantiate_returns409OnNameConflict() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			"existing-name",
			null,
			null,
			null
		);

		// simula container existente
		Container existing = mock(Container.class);
		when(existing.getNames()).thenReturn(new String[]{"/existing-name"});
		when(dockerEngineService.listContainers(true)).thenReturn(List.of(existing));

		ResponseEntity<TemplateInstantiateResponse> response = controller.instantiate("template1", request);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		verify(instantiationService, never()).instantiate(anyString(), anyString(), any(), any(), any());
	}

	@Test
	void instantiate_returns400OnInvalidArgument() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			"my-instance",
			null,
			null,
			null
		);

		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		when(instantiationService.instantiate(anyString(), anyString(), any(), any(), any()))
			.thenThrow(new IllegalArgumentException("invalid name"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.instantiate("template1", request);
		});

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

	@Test
	void instantiate_returns404OnTemplateNotFound() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			"my-instance",
			null,
			null,
			null
		);

		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		when(instantiationService.instantiate(anyString(), anyString(), any(), any(), any()))
			.thenThrow(new NoSuchFileException("template not found"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.instantiate("not-found", request);
		});

		assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
	}

	@Test
	void instantiate_returns500OnIOException() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			"my-instance",
			null,
			null,
			null
		);

		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		when(instantiationService.instantiate(anyString(), anyString(), any(), any(), any()))
			.thenThrow(new IOException("IO error"));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
			controller.instantiate("template1", request);
		});

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
	}

	@Test
	void instantiate_passesOverridesToService() throws Exception {
		TemplateInstantiateRequest request = new TemplateInstantiateRequest(
			"my-instance",
			Map.of("VAR1", "value1"),
			List.of("9090:80"),
			List.of("/custom:/path")
		);

		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		when(instantiationService.instantiate(anyString(), anyString(), any(), any(), any()))
			.thenReturn(new InstantiationResult("cid", List.of()));
		
		// Mock do ClusterService
		com.kryptforge.clusterforge.clusters.ClusterInstance mockInstance = new com.kryptforge.clusterforge.clusters.ClusterInstance();
		mockInstance.setName("my-instance");
		when(clusterService.create(anyString(), anyString(), any())).thenReturn(mockInstance);
		when(clusterService.updateContainerId(any(), anyString())).thenReturn(mockInstance);
		when(clusterService.updateStatus(any(), any())).thenReturn(mockInstance);
		when(clusterService.list()).thenReturn(List.of());

		controller.instantiate("template1", request);

		verify(instantiationService).instantiate(
			eq("template1"),
			eq("my-instance"),
			eq(request.env()),
			eq(request.ports()),
			eq(request.binds())
		);
	}
}

