package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.EventsCmd;
import com.github.dockerjava.api.model.Event;
import com.kryptforge.clusterforge.docker.DockerConnection;

/**
 * Testes unitários para DockerEventsListener.
 */
@ExtendWith(MockitoExtension.class)
class DockerEventsListenerTest {

	@Mock
	private DockerConnection dockerConnection;

	@Mock
	private ClusterService clusterService;

	@Mock
	private ClusterRepository clusterRepository;

	@Mock
	private DockerClient dockerClient;

	@Mock
	private EventsCmd eventsCmd;

	private DockerEventsListener listener;

	@BeforeEach
	void setup() {
		listener = new DockerEventsListener(dockerConnection, clusterService, clusterRepository, true);
	}

	@Test
	@DisplayName("mapEventToStatus deve mapear ação 'start' para ACTIVE")
	void mapEventToStatus_startMapsToActive() throws Exception {
		Event event = createEvent("container-id-123", "start", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.STOPPED);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		when(clusterService.updateStatus(any(), any())).thenAnswer(inv -> {
			instance.setStatus(inv.getArgument(1));
			return instance;
		});
		
		// Simula evento sendo processado
		simulateEvent(event);
		
		ArgumentCaptor<ClusterStatus> statusCaptor = ArgumentCaptor.forClass(ClusterStatus.class);
		verify(clusterService).updateStatus(any(), statusCaptor.capture());
		assertEquals(ClusterStatus.ACTIVE, statusCaptor.getValue());
	}

	@Test
	@DisplayName("mapEventToStatus deve mapear ação 'stop' para STOPPED")
	void mapEventToStatus_stopMapsToStopped() throws Exception {
		Event event = createEvent("container-id-123", "stop", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.ACTIVE);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		when(clusterService.updateStatus(any(), any())).thenAnswer(inv -> {
			instance.setStatus(inv.getArgument(1));
			return instance;
		});
		
		simulateEvent(event);
		
		ArgumentCaptor<ClusterStatus> statusCaptor = ArgumentCaptor.forClass(ClusterStatus.class);
		verify(clusterService).updateStatus(any(), statusCaptor.capture());
		assertEquals(ClusterStatus.STOPPED, statusCaptor.getValue());
	}

	@Test
	@DisplayName("mapEventToStatus deve mapear ação 'die' para STOPPED")
	void mapEventToStatus_dieMapsToStopped() throws Exception {
		Event event = createEvent("container-id-123", "die", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.ACTIVE);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		when(clusterService.updateStatus(any(), any())).thenAnswer(inv -> {
			instance.setStatus(inv.getArgument(1));
			return instance;
		});
		
		simulateEvent(event);
		
		ArgumentCaptor<ClusterStatus> statusCaptor = ArgumentCaptor.forClass(ClusterStatus.class);
		verify(clusterService).updateStatus(any(), statusCaptor.capture());
		assertEquals(ClusterStatus.STOPPED, statusCaptor.getValue());
	}

	@Test
	@DisplayName("mapEventToStatus deve mapear ação 'create' para PENDING")
	void mapEventToStatus_createMapsToPending() throws Exception {
		Event event = createEvent("container-id-123", "create", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.PENDING);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		
		simulateEvent(event);
		
		// Não deve atualizar se já está no mesmo status
		verify(clusterService, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("mapEventToStatus deve mapear ação 'remove' para DELETED e limpar containerId")
	void mapEventToStatus_removeMapsToDeletedAndClearsContainerId() throws Exception {
		Event event = createEvent("container-id-123", "remove", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.STOPPED);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		when(clusterService.updateStatus(any(), any())).thenAnswer(inv -> {
			instance.setStatus(inv.getArgument(1));
			return instance;
		});
		when(clusterService.updateContainerId(any(), isNull())).thenAnswer(inv -> {
			instance.setContainerId(null);
			return instance;
		});
		
		simulateEvent(event);
		
		ArgumentCaptor<ClusterStatus> statusCaptor = ArgumentCaptor.forClass(ClusterStatus.class);
		verify(clusterService).updateStatus(any(), statusCaptor.capture());
		assertEquals(ClusterStatus.DELETED, statusCaptor.getValue());
		verify(clusterService).updateContainerId(any(), isNull());
	}

	@Test
	@DisplayName("handleDockerEvent deve ignorar eventos de containers não encontrados no banco")
	void handleDockerEvent_ignoresUnknownContainers() throws Exception {
		Event event = createEvent("unknown-container", "start", null);
		
		when(clusterRepository.findByContainerId("unknown-container")).thenReturn(Optional.empty());
		
		simulateEvent(event);
		
		verify(clusterService, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("handleDockerEvent deve ignorar eventos com action null")
	void handleDockerEvent_ignoresEventsWithNullAction() throws Exception {
		Event event = createEvent("container-id-123", null, null);
		
		simulateEvent(event);
		
		verify(clusterRepository, never()).findByContainerId(any());
		verify(clusterService, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("handleDockerEvent deve ignorar eventos com containerId null")
	void handleDockerEvent_ignoresEventsWithNullContainerId() throws Exception {
		Event event = createEvent(null, "start", null);
		
		simulateEvent(event);
		
		verify(clusterRepository, never()).findByContainerId(any());
		verify(clusterService, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("handleDockerEvent deve ignorar ações desconhecidas")
	void handleDockerEvent_ignoresUnknownActions() throws Exception {
		Event event = createEvent("container-id-123", "attach", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.ACTIVE);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		
		simulateEvent(event);
		
		verify(clusterService, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("handleDockerEvent não deve atualizar se status já está correto")
	void handleDockerEvent_doesNotUpdateIfStatusAlreadyCorrect() throws Exception {
		Event event = createEvent("container-id-123", "start", null);
		ClusterInstance instance = createInstance("test-instance", "container-id-123", ClusterStatus.ACTIVE);
		
		when(clusterRepository.findByContainerId("container-id-123")).thenReturn(Optional.of(instance));
		
		simulateEvent(event);
		
		// Não deve atualizar se já está no status correto
		verify(clusterService, never()).updateStatus(any(), any());
	}

	// Métodos auxiliares

	private Event createEvent(String containerId, String action, String status) {
		Event event = mock(Event.class);
		when(event.getId()).thenReturn(containerId);
		when(event.getAction()).thenReturn(action);
		when(event.getStatus()).thenReturn(status);
		return event;
	}

	private ClusterInstance createInstance(String name, String containerId, ClusterStatus status) {
		ClusterInstance instance = new ClusterInstance();
		// ID é gerado automaticamente pela JPA, não precisa setar
		instance.setName(name);
		instance.setContainerId(containerId);
		instance.setStatus(status);
		instance.setTemplateName("test-template");
		return instance;
	}

	/**
	 * Simula um evento sendo processado pelo listener.
	 * Usa reflection para chamar o método privado handleDockerEvent.
	 */
	private void simulateEvent(Event event) throws Exception {
		java.lang.reflect.Method method = DockerEventsListener.class.getDeclaredMethod("handleDockerEvent", Event.class);
		method.setAccessible(true);
		method.invoke(listener, event);
	}
}

