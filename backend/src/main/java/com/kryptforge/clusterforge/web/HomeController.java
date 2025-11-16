package com.kryptforge.clusterforge.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/")
public class HomeController {

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> root() {
		Map<String, Object> payload = new HashMap<>();
		payload.put("name", "clusterforge-backend");
		payload.put("status", "OK");
		payload.put("version", "0.0.1-SNAPSHOT");
		payload.put("message", "Backend ativo. Consulte a documentação dos endpoints ou use /api.");
		return payload;
	}
}


