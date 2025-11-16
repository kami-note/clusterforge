package com.kryptforge.clusterforge.web;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.templates.TemplateInstantiationService;
import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateSummary;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateRequest;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateResponse;

@RestController
@RequestMapping(path = "/api/templates", produces = MediaType.APPLICATION_JSON_VALUE)
public class TemplateController {

	private final TemplateService templateService;
	private final TemplateInstantiationService instantiationService;

	public TemplateController(TemplateService templateService, TemplateInstantiationService instantiationService) {
		this.templateService = templateService;
		this.instantiationService = instantiationService;
	}

	@GetMapping
	public List<TemplateSummary> listTemplates() {
		try {
			return templateService.listTemplates();
		} catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao ler templates", e);
		}
	}

	@GetMapping("/{name}")
	public TemplateDetail getTemplate(@PathVariable("name") String name) {
		try {
			return templateService.getTemplate(name);
		} catch (NoSuchFileException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		} catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao ler template", e);
		}
	}

	@PostMapping(path = "/{name}/instantiate", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TemplateInstantiateResponse> instantiate(
		@PathVariable("name") String name,
		@RequestBody TemplateInstantiateRequest request
	) {
		try {
			String containerId = instantiationService.instantiate(
				name,
				request.name(),
				request.env(),
				request.ports(),
				request.binds()
			);
			return ResponseEntity.status(HttpStatus.CREATED)
				.body(new TemplateInstantiateResponse(containerId, request.name()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		} catch (NoSuchFileException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao instanciar template", e);
		}
	}
}


