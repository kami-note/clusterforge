package com.kryptforge.clusterforge.ftp;

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
import com.kryptforge.clusterforge.docker.ClusterUserManager;
import com.kryptforge.clusterforge.docker.DockerConnection;

/**
 * Testes unitários para DefaultFtpService.
 */
class DefaultFtpServiceTest {

	@TempDir
	Path tempDir;

	private DockerConnection dockerConnection;
	private DockerClient dockerClient;
	private DefaultFtpService ftpService;
	private ClusterUserManager userManager;
	private PullImageCmd pullImageCmd;
	private CreateContainerCmd createContainerCmd;
	private CreateContainerResponse createResponse;
	private StartContainerCmd startContainerCmd;
	private InspectContainerCmd inspectContainerCmd;
	private InspectContainerResponse inspectResponse;

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
		when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withUser(anyString())).thenReturn(createContainerCmd);
		when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
		when(dockerClient.inspectContainerCmd(anyString())).thenReturn(inspectContainerCmd);
		when(dockerClient.removeContainerCmd(anyString())).thenReturn(mock(RemoveContainerCmd.class));
		when(dockerClient.stopContainerCmd(anyString())).thenReturn(mock(StopContainerCmd.class));

		when(createContainerCmd.exec()).thenReturn(createResponse);
		when(createResponse.getId()).thenReturn("ftp-container-id-123");
		when(inspectContainerCmd.exec()).thenReturn(inspectResponse);
		
		// Mock do ContainerState
		com.github.dockerjava.api.command.InspectContainerResponse.ContainerState state = 
			mock(com.github.dockerjava.api.command.InspectContainerResponse.ContainerState.class);
		when(inspectResponse.getState()).thenReturn(state);
		when(state.getRunning()).thenReturn(true);

		userManager = mock(ClusterUserManager.class);
		when(userManager.generateUid(anyString())).thenReturn(1000);
		when(userManager.generateGid(anyString())).thenReturn(1000);
		when(userManager.formatUserString(anyInt(), anyInt())).thenReturn("1000:1000");
		doNothing().when(userManager).adjustVolumePermissions(any(), anyInt(), anyInt());

		ftpService = new DefaultFtpService(dockerConnection, userManager);
	}

	@Test
	@DisplayName("createFtpServer deve criar servidor FTP com sucesso")
	void createFtpServer_shouldCreateFtpServerSuccessfully() throws Exception {
		// Arrange
		String containerName = "test-container";
		Path volumePath = tempDir.resolve("volume");
		Files.createDirectories(volumePath);
		int ftpPort = 9000;

		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withExposedPorts(any(com.github.dockerjava.api.model.ExposedPort[].class))).thenReturn(createContainerCmd);
		when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);

		// Act
		FtpService.FtpServerInfo result = ftpService.createFtpServer(
			containerName,
			volumePath.toString(),
			ftpPort,
			null,
			null
		);

		// Assert
		assertNotNull(result);
		assertEquals("ftp-container-id-123", result.containerId());
		assertEquals(ftpPort, result.hostPort());
		assertNotNull(result.ftpUser());
		assertNotNull(result.ftpPassword());
		assertEquals(volumePath.toString(), result.volumePath());

		verify(dockerClient).pullImageCmd("fauria/vsftpd:latest");
		verify(dockerClient).createContainerCmd("fauria/vsftpd:latest");
		verify(dockerClient).startContainerCmd("ftp-container-id-123");
		verify(createContainerCmd).withName(containerName + "-ftp");
	}

	@Test
	@DisplayName("createFtpServer deve usar usuário e senha fornecidos")
	void createFtpServer_shouldUseProvidedUserAndPassword() throws Exception {
		// Arrange
		String containerName = "test-container";
		Path volumePath = tempDir.resolve("volume");
		Files.createDirectories(volumePath);
		int ftpPort = 9000;
		String ftpUser = "customuser";
		String ftpPassword = "custompass123";

		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withExposedPorts(any(com.github.dockerjava.api.model.ExposedPort[].class))).thenReturn(createContainerCmd);
		when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);

		// Act
		FtpService.FtpServerInfo result = ftpService.createFtpServer(
			containerName,
			volumePath.toString(),
			ftpPort,
			ftpUser,
			ftpPassword
		);

		// Assert
		assertNotNull(result);
		assertEquals(ftpUser, result.ftpUser());
		assertEquals(ftpPassword, result.ftpPassword());
	}

	@Test
	@DisplayName("createFtpServer deve criar diretório de volume se não existir")
	void createFtpServer_shouldCreateVolumeDirectoryIfNotExists() throws Exception {
		// Arrange
		String containerName = "test-container";
		Path volumePath = tempDir.resolve("new-volume");
		int ftpPort = 9000;

		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withExposedPorts(any(com.github.dockerjava.api.model.ExposedPort[].class))).thenReturn(createContainerCmd);
		when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);

		// Act
		FtpService.FtpServerInfo result = ftpService.createFtpServer(
			containerName,
			volumePath.toString(),
			ftpPort,
			null,
			null
		);

		// Assert
		assertTrue(Files.exists(volumePath));
		assertNotNull(result);
	}

	@Test
	@DisplayName("createFtpServer deve lançar exceção se porta inválida")
	void createFtpServer_shouldThrowExceptionForInvalidPort() throws Exception {
		// Arrange
		String containerName = "test-container";
		Path volumePath = tempDir.resolve("volume");
		Files.createDirectories(volumePath);

		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			ftpService.createFtpServer(containerName, volumePath.toString(), 0, null, null);
		});

		assertThrows(IllegalArgumentException.class, () -> {
			ftpService.createFtpServer(containerName, volumePath.toString(), 70000, null, null);
		});
	}

	@Test
	@DisplayName("createFtpServer deve remover container se falhar ao iniciar")
	void createFtpServer_shouldRemoveContainerIfStartFails() throws Exception {
		// Arrange
		String containerName = "test-container";
		Path volumePath = tempDir.resolve("volume");
		Files.createDirectories(volumePath);
		int ftpPort = 9000;

		when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
		when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
		when(createContainerCmd.withExposedPorts(any(com.github.dockerjava.api.model.ExposedPort[].class))).thenReturn(createContainerCmd);
		when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);
		when(startContainerCmd.exec()).thenThrow(new RuntimeException("Falha ao iniciar"));

		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
		when(dockerClient.removeContainerCmd("ftp-container-id-123")).thenReturn(removeCmd);
		when(removeCmd.withForce(true)).thenReturn(removeCmd);

		// Act & Assert
		assertThrows(IllegalStateException.class, () -> {
			ftpService.createFtpServer(containerName, volumePath.toString(), ftpPort, null, null);
		});

		verify(dockerClient).removeContainerCmd("ftp-container-id-123");
		verify(removeCmd).withForce(true);
		verify(removeCmd).exec();
	}

	@Test
	@DisplayName("removeFtpServer deve remover container FTP")
	void removeFtpServer_shouldRemoveFtpContainer() {
		// Arrange
		String ftpContainerId = "ftp-container-id-123";
		StopContainerCmd stopCmd = mock(StopContainerCmd.class);
		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);

		when(dockerClient.stopContainerCmd(ftpContainerId)).thenReturn(stopCmd);
		when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
		when(dockerClient.removeContainerCmd(ftpContainerId)).thenReturn(removeCmd);
		when(removeCmd.withForce(true)).thenReturn(removeCmd);

		// Act
		ftpService.removeFtpServer(ftpContainerId);

		// Assert
		verify(dockerClient).stopContainerCmd(ftpContainerId);
		verify(dockerClient).removeContainerCmd(ftpContainerId);
		verify(removeCmd).withForce(true);
		verify(removeCmd).exec();
	}

	@Test
	@DisplayName("removeFtpServer deve tratar container não encontrado graciosamente")
	void removeFtpServer_shouldHandleNotFoundGracefully() {
		// Arrange
		String ftpContainerId = "non-existent-id";
		StopContainerCmd stopCmd = mock(StopContainerCmd.class);
		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);

		when(dockerClient.stopContainerCmd(ftpContainerId)).thenReturn(stopCmd);
		when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
		when(stopCmd.exec()).thenThrow(new RuntimeException("Not found"));
		when(dockerClient.removeContainerCmd(ftpContainerId)).thenReturn(removeCmd);
		when(removeCmd.withForce(true)).thenReturn(removeCmd);
		// Simula exceção de "not found" que deve ser tratada graciosamente
		when(removeCmd.exec()).thenThrow(new RuntimeException("No such container: not found"));

		// Act - não deve lançar exceção (tratamento interno)
		// O método trata exceções de "not found" graciosamente
		assertDoesNotThrow(() -> {
			ftpService.removeFtpServer(ftpContainerId);
		});
	}

	@Test
	@DisplayName("removeFtpServer deve retornar silenciosamente se containerId for vazio")
	void removeFtpServer_shouldReturnSilentlyIfContainerIdIsEmpty() {
		// Act & Assert
		assertDoesNotThrow(() -> {
			ftpService.removeFtpServer(null);
			ftpService.removeFtpServer("");
			ftpService.removeFtpServer("   ");
		});
	}

	@Test
	@DisplayName("isFtpServerRunning deve retornar true se container está rodando")
	void isFtpServerRunning_shouldReturnTrueIfRunning() {
		// Arrange
		String ftpContainerId = "ftp-container-id-123";
		com.github.dockerjava.api.command.InspectContainerResponse.ContainerState state = 
			mock(com.github.dockerjava.api.command.InspectContainerResponse.ContainerState.class);
		when(inspectResponse.getState()).thenReturn(state);
		when(state.getRunning()).thenReturn(true);

		// Act
		boolean result = ftpService.isFtpServerRunning(ftpContainerId);

		// Assert
		assertTrue(result);
		verify(dockerClient).inspectContainerCmd(ftpContainerId);
	}

	@Test
	@DisplayName("isFtpServerRunning deve retornar false se container não está rodando")
	void isFtpServerRunning_shouldReturnFalseIfNotRunning() {
		// Arrange
		String ftpContainerId = "ftp-container-id-123";
		com.github.dockerjava.api.command.InspectContainerResponse.ContainerState state = 
			mock(com.github.dockerjava.api.command.InspectContainerResponse.ContainerState.class);
		when(inspectResponse.getState()).thenReturn(state);
		when(state.getRunning()).thenReturn(false);

		// Act
		boolean result = ftpService.isFtpServerRunning(ftpContainerId);

		// Assert
		assertFalse(result);
	}

	@Test
	@DisplayName("isFtpServerRunning deve retornar false se container não existe")
	void isFtpServerRunning_shouldReturnFalseIfContainerNotFound() {
		// Arrange
		String ftpContainerId = "non-existent-id";
		when(inspectContainerCmd.exec()).thenThrow(new RuntimeException("Not found"));

		// Act
		boolean result = ftpService.isFtpServerRunning(ftpContainerId);

		// Assert
		assertFalse(result);
	}

	@Test
	@DisplayName("isFtpServerRunning deve retornar false se containerId for vazio")
	void isFtpServerRunning_shouldReturnFalseIfContainerIdIsEmpty() {
		// Act & Assert
		assertFalse(ftpService.isFtpServerRunning(null));
		assertFalse(ftpService.isFtpServerRunning(""));
		assertFalse(ftpService.isFtpServerRunning("   "));
	}
}

