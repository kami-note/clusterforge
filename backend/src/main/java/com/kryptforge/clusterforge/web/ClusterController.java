package com.kryptforge.clusterforge.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterCreateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterResponse;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterStatusUpdateRequest;
import com.kryptforge.clusterforge.clusters.dto.ClusterDtos.ClusterUpdateParamsRequest;

@RestController
@RequestMapping(path = "/api/clusters", produces = MediaType.APPLICATION_JSON_VALUE)
public class ClusterController {

	private final ClusterService service;

	public ClusterController(ClusterService service) {
		this.service = service;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ClusterResponse> create(@RequestBody ClusterCreateRequest req) {
		try {
			ClusterInstance c = service.create(req.name(), req.templateName(), req.toParams());
			return ResponseEntity.status(HttpStatus.CREATED).body(ClusterResponse.from(c));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@GetMapping
	public List<ClusterResponse> list() {
		return service.list().stream().map(ClusterResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ClusterResponse get(@PathVariable("id") UUID id) {
		return service.get(id).map(ClusterResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cluster não encontrado"));
	}

	@PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateStatus(@PathVariable("id") UUID id, @RequestBody ClusterStatusUpdateRequest req) {
		try {
			return ClusterResponse.from(service.updateStatus(id, req.status()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ClusterResponse updateParams(@PathVariable("id") UUID id, @RequestBody ClusterUpdateParamsRequest req) {
		try {
			return ClusterResponse.from(service.updateParams(id, req.toParams()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}


