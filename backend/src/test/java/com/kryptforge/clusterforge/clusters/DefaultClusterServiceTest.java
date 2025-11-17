package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;
import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateFileEntry;
import com.kryptforge.clusterforge.users.CurrentUser;
import com.kryptforge.clusterforge.users.Role;
import com.kryptforge.clusterforge.users.User;

class DefaultClusterServiceTest {

	private ClusterRepository repository;
	private TemplateService templateService;
	private DockerEngineService dockerEngineService;
	private PortManager portManager;
	private CurrentUser currentUser;
	private com.kryptforge.clusterforge.ftp.FtpService ftpService;
	private DefaultClusterService service;
	private User mockUser;

	@BeforeEach
	void setup() {
		repository = mock(ClusterRepository.class);
		templateService = mock(TemplateService.class);
		dockerEngineService = mock(DockerEngineService.class);
		portManager = mock(PortManager.class);
		currentUser = mock(CurrentUser.class);
		ftpService = mock(com.kryptforge.clusterforge.ftp.FtpService.class);
		
		// Cria usuário mock para todos os testes
		mockUser = new User();
		try {
			java.lang.reflect.Field idField = User.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(mockUser, UUID.randomUUID());
			idField.setAccessible(false);
		} catch (Exception e) {
			throw new RuntimeException("Erro ao setar ID do usuário para teste", e);
		}
		mockUser.setUsername("testuser");
		mockUser.setRole(Role.ADMIN);
		
		// Mock CurrentUser para retornar usuário autenticado
		when(currentUser.getCurrentUser()).thenReturn(Optional.of(mockUser));
		
		service = new DefaultClusterService(repository, templateService, dockerEngineService, portManager, currentUser, ftpService);
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
		existing.setOwnerId(mockUser.getId());

		when(repository.findById(any())).thenReturn(Optional.of(existing));
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var updatedStatus = service.updateStatus(UUID.randomUUID(), ClusterStatus.ACTIVE);
		assertEquals(ClusterStatus.ACTIVE, updatedStatus.getStatus());

		var updatedParams = service.updateParams(UUID.randomUUID(), new ClusterService.ClusterParams(Map.of("B","2"), List.of(80,80,443), List.of("/v:/v")));
		assertEquals(Map.of("B","2"), updatedParams.getEnv());
		assertEquals(List.of(80,443), updatedParams.getPorts());
	}

	@Test
	@DisplayName("delete deve remover container e registro do banco")
	void delete_removesContainerAndDatabase() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		// ID é gerado automaticamente pela JPA, não precisa setar
		instance.setName("test-cluster");
		instance.setContainerId("container-123");
		instance.setPorts(List.of(9000, 9001));
		instance.setStatus(ClusterStatus.ACTIVE);
		instance.setOwnerId(mockUser.getId());

		when(repository.findById(id)).thenReturn(Optional.of(instance));
		doNothing().when(dockerEngineService).stopContainer(anyString(), anyInt());
		doNothing().when(dockerEngineService).removeContainer(anyString(), anyBoolean(), anyBoolean());
		doNothing().when(portManager).releasePorts(anyList());
		when(repository.save(any(ClusterInstance.class))).thenAnswer(inv -> inv.getArgument(0));
		doNothing().when(repository).deleteById(id);

		// Executa
		service.delete(id);

		// Verifica que parou o container
		verify(dockerEngineService, times(1)).stopContainer("container-123", 10);
		// Verifica que removeu o container
		verify(dockerEngineService, times(1)).removeContainer("container-123", true, false);
		// Verifica que liberou as portas
		verify(portManager, times(1)).releasePorts(List.of(9000, 9001));
		// Verifica que removeu do banco
		verify(repository, times(1)).deleteById(id);
	}

	@Test
	@DisplayName("delete deve funcionar mesmo sem containerId")
	void delete_worksWithoutContainerId() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		instance.setName("test-cluster");
		instance.setContainerId(null);
		instance.setStatus(ClusterStatus.PENDING);
		instance.setOwnerId(mockUser.getId());

		when(repository.findById(id)).thenReturn(Optional.of(instance));
		doNothing().when(repository).deleteById(id);

		// Executa
		service.delete(id);

		// Não deve tentar parar/remover container
		verify(dockerEngineService, never()).stopContainer(anyString(), anyInt());
		verify(dockerEngineService, never()).removeContainer(anyString(), anyBoolean(), anyBoolean());
		// Deve remover do banco
		verify(repository, times(1)).deleteById(id);
	}

	@Test
	@DisplayName("deleteContainer deve parar, remover container e liberar portas")
	void deleteContainer_stopsRemovesContainerAndReleasesPorts() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		instance.setName("test-cluster");
		instance.setContainerId("container-123");
		instance.setPorts(List.of(9000, 9001));
		instance.setStatus(ClusterStatus.ACTIVE);
		instance.setOwnerId(mockUser.getId());

		when(repository.findById(id)).thenReturn(Optional.of(instance));
		doNothing().when(dockerEngineService).stopContainer(anyString(), anyInt());
		doNothing().when(dockerEngineService).removeContainer(anyString(), anyBoolean(), anyBoolean());
		doNothing().when(portManager).releasePorts(anyList());
		when(repository.save(any(ClusterInstance.class))).thenAnswer(inv -> inv.getArgument(0));

		// Executa
		service.deleteContainer(id);

		// Verifica que parou o container
		verify(dockerEngineService, times(1)).stopContainer("container-123", 10);
		// Verifica que removeu o container
		verify(dockerEngineService, times(1)).removeContainer("container-123", true, false);
		// Verifica que liberou as portas
		verify(portManager, times(1)).releasePorts(List.of(9000, 9001));
		// Verifica que atualizou o registro (limpa containerId e status DELETED)
		verify(repository, times(1)).save(argThat(inst -> 
			inst.getContainerId() == null && inst.getStatus() == ClusterStatus.DELETED
		));
	}

	@Test
	@DisplayName("deleteContainer deve continuar mesmo se falhar ao parar container")
	void deleteContainer_continuesIfStopFails() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		instance.setName("test-cluster");
		instance.setContainerId("container-123");
		instance.setPorts(List.of(9000));
		instance.setOwnerId(mockUser.getId());

		when(repository.findById(id)).thenReturn(Optional.of(instance));
		doThrow(new RuntimeException("Container já parado")).when(dockerEngineService).stopContainer(anyString(), anyInt());
		doNothing().when(dockerEngineService).removeContainer(anyString(), anyBoolean(), anyBoolean());
		doNothing().when(portManager).releasePorts(anyList());
		when(repository.save(any(ClusterInstance.class))).thenAnswer(inv -> inv.getArgument(0));

		// Executa - não deve lançar exceção
		assertDoesNotThrow(() -> service.deleteContainer(id));

		// Deve tentar remover mesmo após falha ao parar
		verify(dockerEngineService, times(1)).removeContainer("container-123", true, false);
		verify(portManager, times(1)).releasePorts(List.of(9000));
	}

	@Test
	@DisplayName("deleteContainer deve retornar sem erro se não houver containerId")
	void deleteContainer_returnsEarlyIfNoContainerId() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		instance.setName("test-cluster");
		instance.setContainerId(null);
		instance.setOwnerId(mockUser.getId());

		when(repository.findById(id)).thenReturn(Optional.of(instance));

		// Executa
		service.deleteContainer(id);

		// Não deve tentar parar/remover container
		verify(dockerEngineService, never()).stopContainer(anyString(), anyInt());
		verify(dockerEngineService, never()).removeContainer(anyString(), anyBoolean(), anyBoolean());
		verify(portManager, never()).releasePorts(anyList());
	}

	@Test
	@DisplayName("deleteFromDatabase deve remover apenas do banco")
	void deleteFromDatabase_removesOnlyFromDatabase() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		instance.setName("test-cluster");
		instance.setContainerId("container-123");

		when(repository.findById(id)).thenReturn(Optional.of(instance));
		doNothing().when(repository).deleteById(id);

		// Executa
		service.deleteFromDatabase(id);

		// Não deve tocar no container
		verify(dockerEngineService, never()).stopContainer(anyString(), anyInt());
		verify(dockerEngineService, never()).removeContainer(anyString(), anyBoolean(), anyBoolean());
		verify(portManager, never()).releasePorts(anyList());
		// Deve remover do banco
		verify(repository, times(1)).deleteById(id);
	}

	@Test
	@DisplayName("delete deve lançar exceção se instância não existir")
	void delete_throwsIfInstanceNotFound() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			service.delete(id);
		});

		assertTrue(ex.getMessage().contains("não encontrado"));
		verify(repository, never()).deleteById(any());
	}

	@Test
	@DisplayName("deleteContainer deve lançar exceção se instância não existir")
	void deleteContainer_throwsIfInstanceNotFound() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			service.deleteContainer(id);
		});

		assertTrue(ex.getMessage().contains("não encontrado"));
	}

	@Test
	@DisplayName("deleteFromDatabase deve lançar exceção se instância não existir")
	void deleteFromDatabase_throwsIfInstanceNotFound() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			service.deleteFromDatabase(id);
		});

		assertTrue(ex.getMessage().contains("não encontrado"));
		verify(repository, never()).deleteById(any());
	}

	@Test
	@DisplayName("deleteContainer deve lançar exceção se falhar ao remover container")
	void deleteContainer_throwsIfRemoveFails() {
		UUID id = UUID.randomUUID();
		ClusterInstance instance = new ClusterInstance();
		instance.setName("test-cluster");
		instance.setContainerId("container-123");
		instance.setPorts(List.of(9000));
		instance.setOwnerId(mockUser.getId());

		when(repository.findById(id)).thenReturn(Optional.of(instance));
		doNothing().when(dockerEngineService).stopContainer(anyString(), anyInt());
		doThrow(new RuntimeException("Container não encontrado")).when(dockerEngineService).removeContainer(anyString(), anyBoolean(), anyBoolean());

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
			service.deleteContainer(id);
		});

		assertTrue(ex.getMessage().contains("Falha ao remover container"));
		// Não deve liberar portas se falhar
		verify(portManager, never()).releasePorts(anyList());
	}
}


