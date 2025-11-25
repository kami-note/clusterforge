package com.kryptforge.clusterforge.docker.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento de log emitido via SSE.
 */
public final class ContainerLogEvent {

	private final String containerId;
	private final String stream;
	private final String message;
	private final Instant timestamp;
	private final long epochSecond;

	public ContainerLogEvent(String containerId, String stream, String message, Instant timestamp) {
		this.containerId = Objects.requireNonNull(containerId, "containerId");
		this.stream = (stream != null && !stream.isBlank()) ? stream : "STDOUT";
		this.message = message != null ? message : "";
		Instant safeTimestamp = timestamp != null ? timestamp : Instant.now();
		this.timestamp = safeTimestamp;
		this.epochSecond = safeTimestamp.getEpochSecond();
	}

	public String getContainerId() {
		return containerId;
	}

	public String getStream() {
		return stream;
	}

	public String getMessage() {
		return message;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public long getEpochSecond() {
		return epochSecond;
	}
}

