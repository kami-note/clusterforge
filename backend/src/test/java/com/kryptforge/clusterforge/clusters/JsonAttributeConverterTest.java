package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

class JsonAttributeConverterTest {

	@Test
	void serializeAndDeserialize_MapAndList() {
		var mapConv = new JsonAttributeConverter<Map<String,String>>(new TypeReference<Map<String,String>>(){}) {};
		var listConv = new JsonAttributeConverter<List<Integer>>(new TypeReference<List<Integer>>(){}) {};

		Map<String,String> env = Map.of("A","1","B","2");
		String jsonEnv = mapConv.convertToDatabaseColumn(env);
		assertNotNull(jsonEnv);
		Map<String,String> backEnv = mapConv.convertToEntityAttribute(jsonEnv);
		assertEquals(env, backEnv);

		List<Integer> ports = List.of(80, 443);
		String jsonPorts = listConv.convertToDatabaseColumn(ports);
		assertNotNull(jsonPorts);
		List<Integer> backPorts = listConv.convertToEntityAttribute(jsonPorts);
		assertEquals(ports, backPorts);
	}
}


