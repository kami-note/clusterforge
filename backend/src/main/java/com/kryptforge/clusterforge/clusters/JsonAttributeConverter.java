package com.kryptforge.clusterforge.clusters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Conversor genérico para armazenar JSON em colunas TEXT.
 * Usa TypeReference<T> fornecida pela subclasse anônima.
 */
public abstract class JsonAttributeConverter<T> implements AttributeConverter<T, String> {

	private final ObjectMapper mapper = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private final TypeReference<T> typeRef;

	protected JsonAttributeConverter(TypeReference<T> typeRef) {
		this.typeRef = typeRef;
	}

	@Override
	public String convertToDatabaseColumn(T attribute) {
		if (attribute == null) return null;
		try {
			return mapper.writeValueAsString(attribute);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Falha ao serializar JSON", e);
		}
	}

	@Override
	public T convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) return null;
		try {
			return mapper.readValue(dbData, typeRef);
		} catch (Exception e) {
			throw new IllegalArgumentException("Falha ao desserializar JSON", e);
		}
	}
}


