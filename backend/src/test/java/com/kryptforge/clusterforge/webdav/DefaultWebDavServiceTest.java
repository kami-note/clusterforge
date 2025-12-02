package com.kryptforge.clusterforge.webdav;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.kryptforge.clusterforge.docker.ClusterUserManager;
import com.kryptforge.clusterforge.docker.DockerConnection;

import java.util.Collections;
import java.util.List;

class DefaultWebDavServiceTest {

	@TempDir
	Path tempDir;

	private DockerConnection dockerConnection;
	private DockerClient dockerClient;
	private PullImageCmd pullImageCmd;
	private CreateContainerCmd createContainerCmd;
	private CreateContainerResponse createResponse;
	private StartContainerCmd startContainerCmd;
	private InspectContainerCmd inspectContainerCmd;
	private InspectContainerResponse inspectResponse;
	private DefaultWebDavService webDavService;
	private WebDavProperties properties;
	private ClusterUserManager userManager;

	@BeforeEach
	void setup() {
		dockerConnection = mock(DockerConnection.class);
		dockerClient = mock(DockerClient.class);
		pullImageCmd = mock(PullImageCmd.class);
		createContainerCmd = mock(CreateContainerCmd.class);
		createResponse = mock(CreateContainerResponse.class);
		startContainerCmd = mock(StartContainerCmd.class);
		inspectContainerCmd = mock(InspectContainerCmd.class);
		inspectResponse = mock(InspectContainerResponse.class);

		when(dockerConnection.getClient()).thenReturn(dockerClient);
		when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);
		@SuppressWarnings("unchecked")
		com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.PullResponseItem> callback =
			mock(com.github.dockerjava.api.async.ResultCallback.Adapter.class);
		when(pullImageCmd.start()).thenReturn(callback);
		try {
			doReturn(callback).when(callback).awaitCompletion();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withUser(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withExposedPorts(any(com.github.dockerjava.api.model.ExposedPort[].class))).thenReturn(createContainerCmd);
		when(createContainerCmd.exec()).thenReturn(createResponse);
		when(createResponse.getId()).thenReturn("webdav-container-id");
		when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);

		when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
		when(inspectContainerCmd.exec()).thenReturn(inspectResponse);
		var state = mock(InspectContainerResponse.ContainerState.class);
		when(inspectResponse.getState()).thenReturn(state);
		when(state.getRunning()).thenReturn(true);

		when(dockerClient.removeContainerCmd(anyString())).thenReturn(mock(RemoveContainerCmd.class));
		when(dockerClient.stopContainerCmd(anyString())).thenReturn(mock(StopContainerCmd.class));
		
		// Mock para listContainersCmd (usado na verificação prévia e resolução de conflitos)
		ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
		when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
		when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
		when(listContainersCmd.exec()).thenReturn(Collections.emptyList());

		userManager = mock(ClusterUserManager.class);
		when(userManager.generateUid(anyString())).thenReturn(1000);
		when(userManager.generateGid(anyString())).thenReturn(1000);
		when(userManager.formatUserString(anyInt(), anyInt())).thenReturn("1000:1000");
		doNothing().when(userManager).adjustVolumePermissions(any(), anyInt(), anyInt());

		properties = new WebDavProperties();
		properties.setImage("test/webdav:latest");
		webDavService = new DefaultWebDavService(dockerConnection, properties, userManager);
	}

	@Test
	@DisplayName("createWebDavServer deve criar container WebDAV com sucesso")
	void createWebDavServer_shouldCreateWebDavContainer() throws Exception {
		Path volume = tempDir.resolve("volume");
		Files.createDirectories(volume);

		WebDavService.WebDavServerInfo info = webDavService.createWebDavServer(
			"test-instance",
			volume.toString(),
			9100,
			"user",
			"pass"
		);

		assertNotNull(info);
		assertEquals("webdav-container-id", info.containerId());
		assertEquals(9100, info.hostPort());
		assertEquals("user", info.username());
		assertEquals("pass", info.password());
		verify(dockerClient).createContainerCmd("test/webdav:latest");
		verify(dockerClient).startContainerCmd("webdav-container-id");
	}

	@Test
	@DisplayName("isWebDavServerRunning deve retornar true quando container está em execução")
	void isWebDavServerRunning_shouldReturnTrue() {
		boolean running = webDavService.isWebDavServerRunning("webdav-container-id");
		assertTrue(running);
		verify(dockerClient).inspectContainerCmd("webdav-container-id");
	}

	@Test
	@DisplayName("removeWebDavServer deve remover container com sucesso")
	void removeWebDavServer_shouldRemoveContainer() {
		StopContainerCmd stopCmd = mock(StopContainerCmd.class);
		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);

		when(dockerClient.stopContainerCmd("webdav-container-id")).thenReturn(stopCmd);
		when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
		when(dockerClient.removeContainerCmd("webdav-container-id")).thenReturn(removeCmd);
		when(removeCmd.withForce(true)).thenReturn(removeCmd);

		assertDoesNotThrow(() -> webDavService.removeWebDavServer("webdav-container-id"));
		verify(stopCmd).withTimeout(10);
		verify(removeCmd).withForce(true);
	}

	@Test
	@DisplayName("createWebDavServer deve verificar e remover container existente antes de criar")
	void createWebDavServer_shouldRemoveExistingContainerBeforeCreating() throws Exception {
		Path volume = tempDir.resolve("volume");
		Files.createDirectories(volume);

		// Mock container existente
		Container existingContainer = mock(Container.class);
		when(existingContainer.getId()).thenReturn("existing-container-id");
		when(existingContainer.getNames()).thenReturn(new String[]{"/test-instance-webdav"});
		
		ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
		when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
		when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
		when(listContainersCmd.exec()).thenReturn(List.of(existingContainer));

		StopContainerCmd stopCmd = mock(StopContainerCmd.class);
		when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
		when(dockerClient.stopContainerCmd("existing-container-id")).thenReturn(stopCmd);
		
		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
		when(removeCmd.withForce(true)).thenReturn(removeCmd);
		when(dockerClient.removeContainerCmd("existing-container-id")).thenReturn(removeCmd);

		WebDavService.WebDavServerInfo info = webDavService.createWebDavServer(
			"test-instance",
			volume.toString(),
			9100,
			"user",
			"pass"
		);

		assertNotNull(info);
		assertEquals("webdav-container-id", info.containerId());
		// Verifica que listou containers (verificação prévia)
		verify(dockerClient, atLeastOnce()).listContainersCmd();
		verify(listContainersCmd, atLeastOnce()).withShowAll(true);
		// Verifica que removeu o container existente
		verify(stopCmd).withTimeout(10);
		verify(stopCmd).exec();
		verify(removeCmd).withForce(true);
		verify(removeCmd).exec();
		// Verifica que criou o novo container
		verify(dockerClient).createContainerCmd("test/webdav:latest");
		verify(dockerClient).startContainerCmd("webdav-container-id");
	}

	@Test
	@DisplayName("createWebDavServer deve resolver conflito 409 removendo container e recriando")
	void createWebDavServer_shouldResolveConflict409() throws Exception {
		Path volume = tempDir.resolve("volume");
		Files.createDirectories(volume);

		// Primeira tentativa de criação lança exceção de conflito
		RuntimeException conflictException = new RuntimeException("Status 409: Conflict. The container name \"/test-instance-webdav\" is already in use");
		when(createContainerCmd.exec())
			.thenThrow(conflictException)
			.thenReturn(createResponse);

		// Mock container existente encontrado durante resolução de conflito
		Container existingContainer = mock(Container.class);
		when(existingContainer.getId()).thenReturn("conflicting-container-id");
		when(existingContainer.getNames()).thenReturn(new String[]{"/test-instance-webdav"});
		
		ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
		when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
		when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
		// Primeira chamada (verificação prévia) retorna vazio, segunda (resolução de conflito) retorna o container
		when(listContainersCmd.exec())
			.thenReturn(Collections.emptyList())
			.thenReturn(List.of(existingContainer));

		StopContainerCmd stopCmd = mock(StopContainerCmd.class);
		when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
		when(dockerClient.stopContainerCmd("conflicting-container-id")).thenReturn(stopCmd);
		
		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
		when(removeCmd.withForce(true)).thenReturn(removeCmd);
		when(dockerClient.removeContainerCmd("conflicting-container-id")).thenReturn(removeCmd);

		WebDavService.WebDavServerInfo info = webDavService.createWebDavServer(
			"test-instance",
			volume.toString(),
			9100,
			"user",
			"pass"
		);

		assertNotNull(info);
		assertEquals("webdav-container-id", info.containerId());
		// Verifica que tentou criar duas vezes (primeira falhou, segunda sucedeu)
		verify(createContainerCmd, times(2)).exec();
		// Verifica que removeu o container conflitante
		verify(stopCmd).exec();
		verify(removeCmd).exec();
	}

	@Test
	@DisplayName("createWebDavServer deve tentar recriar mesmo se container conflitante não for encontrado")
	void createWebDavServer_shouldRetryCreationWhenConflictContainerNotFound() throws Exception {
		Path volume = tempDir.resolve("volume");
		Files.createDirectories(volume);

		// Primeira tentativa de criação lança exceção de conflito
		RuntimeException conflictException = new RuntimeException("Status 409: Conflict. The container name is already in use");
		when(createContainerCmd.exec())
			.thenThrow(conflictException)
			.thenReturn(createResponse);

		ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
		when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
		when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
		// Container não encontrado na lista (race condition)
		when(listContainersCmd.exec())
			.thenReturn(Collections.emptyList())
			.thenReturn(Collections.emptyList());

		WebDavService.WebDavServerInfo info = webDavService.createWebDavServer(
			"test-instance",
			volume.toString(),
			9100,
			"user",
			"pass"
		);

		assertNotNull(info);
		assertEquals("webdav-container-id", info.containerId());
		// Verifica que tentou criar duas vezes (primeira falhou, segunda sucedeu)
		verify(createContainerCmd, times(2)).exec();
	}

	@Test
	@DisplayName("createWebDavServer deve lançar exceção se resolução de conflito falhar")
	void createWebDavServer_shouldThrowExceptionWhenConflictResolutionFails() throws Exception {
		Path volume = tempDir.resolve("volume");
		Files.createDirectories(volume);

		// Primeira tentativa de criação lança exceção de conflito
		RuntimeException conflictException = new RuntimeException("Status 409: Conflict. The container name is already in use");
		when(createContainerCmd.exec()).thenThrow(conflictException);

		ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
		when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
		when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
		// Simula erro ao listar containers durante resolução de conflito
		when(listContainersCmd.exec())
			.thenReturn(Collections.emptyList())
			.thenThrow(new RuntimeException("Failed to list containers"));

		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
			webDavService.createWebDavServer(
				"test-instance",
				volume.toString(),
				9100,
				"user",
				"pass"
			);
		});

		assertTrue(exception.getMessage().contains("Falha ao criar container WebDAV devido a conflito de nome"));
		verify(createContainerCmd, times(1)).exec();
	}

	@Test
	@DisplayName("createWebDavServer deve relançar exceção se não for conflito 409")
	void createWebDavServer_shouldRethrowExceptionWhenNotConflict() throws Exception {
		Path volume = tempDir.resolve("volume");
		Files.createDirectories(volume);

		// Exceção que não é de conflito
		RuntimeException otherException = new RuntimeException("Image not found");
		when(createContainerCmd.exec()).thenThrow(otherException);

		ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
		when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
		when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
		when(listContainersCmd.exec()).thenReturn(Collections.emptyList());

		RuntimeException exception = assertThrows(RuntimeException.class, () -> {
			webDavService.createWebDavServer(
				"test-instance",
				volume.toString(),
				9100,
				"user",
				"pass"
			);
		});

		assertEquals("Image not found", exception.getMessage());
		verify(createContainerCmd, times(1)).exec();
	}
}


