package com.kryptforge.clusterforge.docker.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.github.dockerjava.api.model.Frame;
import com.kryptforge.clusterforge.docker.dto.ContainerLogEvent;

/**
 * Utilitário para converter frames de log Docker em eventos estruturados.
 */
public final class ContainerLogParser {

	private ContainerLogParser() {
	}

	public static ContainerLogEvent parseFrame(String containerId, Frame frame) {
		if (frame == null || frame.getPayload() == null) {
			return null;
		}

		String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
		if (payload.isBlank()) {
			return null;
		}

		Instant timestamp = extractTimestamp(payload);
		String message = stripTimestamp(payload, timestamp != null);

		String stream = frame.getStreamType() != null
			? frame.getStreamType().toString()
			: "STDOUT";

		return new ContainerLogEvent(containerId, stream, message, timestamp);
	}

	private static Instant extractTimestamp(String payload) {
		int spaceIndex = payload.indexOf(' ');
		if (spaceIndex <= 0) {
			return null;
		}

		String candidate = payload.substring(0, spaceIndex).trim();
		if (candidate.isEmpty()) {
			return null;
		}

		try {
			// Docker retorna timestamps no formato ISO-8601 (ex: 2024-01-15T10:30:45.123456789Z)
			// Instant.parse() suporta esse formato
			return Instant.parse(candidate);
		} catch (DateTimeParseException e) {
			// Se falhar, tentar parsear como epoch seconds (número)
			try {
				long epochSeconds = Long.parseLong(candidate);
				return Instant.ofEpochSecond(epochSeconds);
			} catch (NumberFormatException nfe) {
				// Se também falhar, retornar null (sem timestamp)
				// Isso é esperado para logs sem timestamp válido
				return null;
			}
		}
	}

	private static String stripTimestamp(String payload, boolean hasTimestamp) {
		if (!hasTimestamp) {
			return sanitize(payload);
		}

		int firstSpace = payload.indexOf(' ');
		if (firstSpace < 0 || firstSpace + 1 >= payload.length()) {
			return sanitize(payload);
		}

		String withoutTimestamp = payload.substring(firstSpace + 1);
		return sanitize(withoutTimestamp);
	}

	private static String sanitize(String payload) {
		// Remove possíveis caracteres de controle adicionados pelo Docker
		return payload
			.replace("\u0000", "")
			.replace("\u0001", "");
	}
}

