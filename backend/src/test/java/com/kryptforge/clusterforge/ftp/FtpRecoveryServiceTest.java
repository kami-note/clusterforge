package com.kryptforge.clusterforge.ftp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;
import com.kryptforge.clusterforge.templates.TemplateProperties;

/**
 * Testes unitários para FtpRecoveryService.
 */
class FtpRecoveryServiceTest {

	@TempDir
	Path tempDir;

	private ClusterRepository clusterRepository;
	private DockerEngineService dockerEngineService;
	private FtpService ftpService;
	private PortManager portManager;
	private TemplateProperties templateProperties;
	private FtpRecoveryService recoveryService;

	@BeforeEach
	void setup() {
		clusterRepository = mock(ClusterRepository.class);
		dockerEngineService = mock(DockerEngineService.class);
		ftpService = mock(FtpService.class);
		portManager = mock(PortManager.class);
		templateProperties = mock(TemplateProperties.class);

		when(templateProperties.getVolumesBasePath()).thenReturn(tempDir.toString());

		recoveryService = new FtpRecoveryService(
			clusterRepository,
			dockerEngineService,
			ftpService,
			portManager,
			templateProperties,
			true
		);
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar clusters sem containerId")
	void recoverMissingFtpServers_shouldIgnoreClustersWithoutContainerId() {
		// Arrange
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId(null);

		when(clusterRepository.findAll()).thenReturn(List.of(cluster));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(ftpService, never()).createFtpServer(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar clusters DELETED ou ERROR")
	void recoverMissingFtpServers_shouldIgnoreDeletedOrErrorClusters() {
		// Arrange
		ClusterInstance deletedCluster = createCluster("deleted", ClusterStatus.DELETED);
		deletedCluster.setContainerId("container-123");
		
		ClusterInstance errorCluster = createCluster("error", ClusterStatus.ERROR);
		errorCluster.setContainerId("container-456");

		when(clusterRepository.findAll()).thenReturn(List.of(deletedCluster, errorCluster));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(ftpService, never()).createFtpServer(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar clusters com FTP já rodando")
	void recoverMissingFtpServers_shouldIgnoreClustersWithRunningFtp() {
		// Arrange
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId("container-123");
		cluster.setFtpContainerId("ftp-container-123");

		InspectContainerResponse inspect = createInspectResponse(true);
		when(dockerEngineService.inspectContainer("container-123")).thenReturn(inspect);
		when(ftpService.isFtpServerRunning("ftp-container-123")).thenReturn(true);
		when(clusterRepository.findAll()).thenReturn(List.of(cluster));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(ftpService, never()).createFtpServer(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve tentar reiniciar FTP parado antes de recriar")
	void recoverMissingFtpServers_shouldTryToRestartStoppedFtp() {
		// Arrange
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId("container-123");
		cluster.setFtpContainerId("ftp-container-123");

		InspectContainerResponse mainInspect = createInspectResponse(true);
		InspectContainerResponse ftpInspect = createInspectResponse(false);

		when(dockerEngineService.inspectContainer("container-123")).thenReturn(mainInspect);
		when(ftpService.isFtpServerRunning("ftp-container-123")).thenReturn(false);
		when(dockerEngineService.inspectContainer("ftp-container-123")).thenReturn(ftpInspect);
		when(clusterRepository.findAll()).thenReturn(List.of(cluster));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(dockerEngineService).startContainer("ftp-container-123");
		verify(ftpService, never()).createFtpServer(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve criar FTP para cluster sem FTP")
	void recoverMissingFtpServers_shouldCreateFtpForClusterWithoutFtp() throws Exception {
		// Arrange
		Path volumePath = tempDir.resolve("volume");
		Files.createDirectories(volumePath);
		
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId("container-123");
		// Configura volume no formato correto para extractMainVolumePath
		cluster.setVolumes(List.of(volumePath.toString() + ":/data"));
		// Garante que não tem FTP ainda
		cluster.setFtpContainerId(null);
		cluster.setFtpPort(null);

		InspectContainerResponse inspect = createInspectResponse(true, volumePath.toString());
		// Mock do inspectContainer é chamado duas vezes: uma no filtro e outra no recoverFtpForCluster
		// Primeira chamada no filtro, segunda no recoverFtpForCluster
		// Usa thenAnswer para garantir que sempre retorna o inspect
		when(dockerEngineService.inspectContainer("container-123")).thenAnswer(inv -> inspect);
		when(portManager.allocatePort()).thenReturn(9000);
		when(clusterRepository.findAll()).thenReturn(List.of(cluster));
		// Mock do save - pode ser chamado no filtro (se limpar FTP) ou no recoverFtpForCluster
		when(clusterRepository.save(any(ClusterInstance.class))).thenAnswer(inv -> {
			ClusterInstance saved = inv.getArgument(0);
			// Retorna o mesmo objeto para permitir verificação
			return saved;
		});
		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		// Não tem FTP rodando (não tem ftpContainerId, então não será chamado no filtro)

		FtpService.FtpServerInfo ftpInfo = new FtpService.FtpServerInfo(
			"ftp-container-123",
			9000,
			"ftpuser",
			"password123",
			volumePath.toString()
		);
		when(ftpService.createFtpServer(anyString(), anyString(), eq(9000), isNull(), isNull()))
			.thenReturn(ftpInfo);

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert - verifica que o createFtpServer foi chamado (indica que passou pelo filtro e chegou ao recoverFtpForCluster)
		// O volume pode ser extraído dos volumes do cluster ou dos mounts, então usamos anyString
		verify(ftpService, times(1)).createFtpServer(
			eq("test"),
			anyString(), // volumePath pode ser extraído de diferentes formas
			eq(9000),
			isNull(),
			isNull()
		);
		
		// Verifica que o save foi chamado para salvar o cluster com informações do FTP
		ArgumentCaptor<ClusterInstance> clusterCaptor = ArgumentCaptor.forClass(ClusterInstance.class);
		verify(clusterRepository, atLeastOnce()).save(clusterCaptor.capture());
		
		// Verifica se algum dos clusters salvos tem as informações do FTP
		boolean foundFtpInfo = clusterCaptor.getAllValues().stream()
			.anyMatch(c -> "ftp-container-123".equals(c.getFtpContainerId()) 
				&& Integer.valueOf(9000).equals(c.getFtpPort())
				&& "ftpuser".equals(c.getFtpUser())
				&& "password123".equals(c.getFtpPassword()));
		
		assertTrue(foundFtpInfo, "Deveria ter salvo o cluster com informações do FTP");
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar cluster se container principal não existe")
	void recoverMissingFtpServers_shouldIgnoreClusterIfMainContainerNotFound() {
		// Arrange
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId("container-123");

		when(dockerEngineService.inspectContainer("container-123"))
			.thenThrow(new RuntimeException("Container not found"));
		when(clusterRepository.findAll()).thenReturn(List.of(cluster));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(ftpService, never()).createFtpServer(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve liberar porta se criação falhar")
	void recoverMissingFtpServers_shouldReleasePortIfCreationFails() throws Exception {
		// Arrange
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId("container-123");
		cluster.setVolumes(List.of(tempDir.resolve("volume").toString() + ":/data"));

		Path volumePath = tempDir.resolve("volume");
		Files.createDirectories(volumePath);

		InspectContainerResponse inspect = createInspectResponse(true);
		when(dockerEngineService.inspectContainer("container-123")).thenReturn(inspect);
		when(portManager.allocatePort()).thenReturn(9000);
		when(clusterRepository.findAll()).thenReturn(List.of(cluster));
		when(clusterRepository.save(any(ClusterInstance.class))).thenAnswer(inv -> inv.getArgument(0));
		when(dockerEngineService.listContainers(true)).thenReturn(new ArrayList<>());
		when(ftpService.createFtpServer(anyString(), anyString(), eq(9000), anyString(), anyString()))
			.thenThrow(new RuntimeException("Falha ao criar"));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(portManager).releasePort(9000);
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar cluster se já existe container FTP com mesmo nome")
	void recoverMissingFtpServers_shouldIgnoreIfFtpContainerWithSameNameExists() {
		// Arrange
		ClusterInstance cluster = createCluster("test", ClusterStatus.ACTIVE);
		cluster.setContainerId("container-123");

		InspectContainerResponse inspect = createInspectResponse(true);
		when(dockerEngineService.inspectContainer("container-123")).thenReturn(inspect);
		when(portManager.allocatePort()).thenReturn(9000);
		when(clusterRepository.findAll()).thenReturn(List.of(cluster));

		Container existingFtpContainer = mock(Container.class);
		when(existingFtpContainer.getNames()).thenReturn(new String[]{"/test-ftp"});
		when(dockerEngineService.listContainers(true)).thenReturn(List.of(existingFtpContainer));

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert
		verify(ftpService, never()).createFtpServer(anyString(), anyString(), anyInt(), anyString(), anyString());
		verify(portManager).releasePort(9000);
	}

	@Test
	@DisplayName("recoverMissingFtpServers não deve executar se desabilitado")
	void recoverMissingFtpServers_shouldNotExecuteIfDisabled() {
		// Arrange
		FtpRecoveryService disabledService = new FtpRecoveryService(
			clusterRepository,
			dockerEngineService,
			ftpService,
			portManager,
			templateProperties,
			false
		);

		// Act
		disabledService.recoverMissingFtpServers();

		// Assert
		verify(clusterRepository, never()).findAll();
	}

	private ClusterInstance createCluster(String name, ClusterStatus status) {
		ClusterInstance cluster = new ClusterInstance();
		// Usa reflection para setar campos privados ou cria via construtor se disponível
		cluster.setName(name);
		cluster.setTemplateName("test-template");
		cluster.setStatus(status);
		return cluster;
	}

	private InspectContainerResponse createInspectResponse(boolean running) {
		return createInspectResponse(running, tempDir.resolve("volume").toString());
	}
	
	private InspectContainerResponse createInspectResponse(boolean running, String volumePath) {
		InspectContainerResponse inspect = mock(InspectContainerResponse.class);
		InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
		when(state.getRunning()).thenReturn(running);
		when(state.getStatus()).thenReturn(running ? "running" : "exited");
		when(inspect.getState()).thenReturn(state);
		
		// Mock de mounts para extrair volume (sempre retorna um mount)
		InspectContainerResponse.Mount mount = mock(InspectContainerResponse.Mount.class);
		when(mount.getSource()).thenReturn(volumePath);
		when(inspect.getMounts()).thenReturn(java.util.List.of(mount));
		
		return inspect;
	}
}

