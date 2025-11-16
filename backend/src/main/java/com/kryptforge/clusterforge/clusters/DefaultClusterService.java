package com.kryptforge.clusterforge.clusters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kryptforge.clusterforge.templates.TemplateService;

@Service
@Transactional
public class DefaultClusterService implements ClusterService {

	private final ClusterRepository repository;
	private final TemplateService templateService;

	public DefaultClusterService(ClusterRepository repository, TemplateService templateService) {
		this.repository = repository;
		this.templateService = templateService;
	}

	@Override
	public ClusterInstance create(String name, String templateName, ClusterParams params) {
		if (!StringUtils.hasText(name)) {
			throw new IllegalArgumentException("name vazio");
		}
		if (!StringUtils.hasText(templateName)) {
			throw new IllegalArgumentException("templateName vazio");
		}
		if (repository.existsByName(name)) {
			throw new IllegalArgumentException("name já utilizado");
		}
		// valida existência do template
		try {
			templateService.getTemplate(templateName);
		} catch (Exception e) {
			throw new IllegalArgumentException("templateName inexistente: " + templateName, e);
		}

		ClusterInstance c = new ClusterInstance();
		c.setName(name);
		c.setTemplateName(templateName);
		c.setStatus(ClusterStatus.PENDING);
		if (params != null) {
			c.setEnv(params.env());
			c.setPorts(normalizePorts(params.ports()));
			c.setVolumes(params.volumes());
		}
		return repository.save(c);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ClusterInstance> list() {
		return repository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ClusterInstance> get(UUID id) {
		return repository.findById(id);
	}

	@Override
	public ClusterInstance updateStatus(UUID id, ClusterStatus status) {
		ClusterInstance c = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		c.setStatus(status != null ? status : c.getStatus());
		return repository.save(c);
	}

	@Override
	public ClusterInstance updateParams(UUID id, ClusterParams params) {
		ClusterInstance c = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		if (params != null) {
			if (params.env() != null) c.setEnv(params.env());
			if (params.ports() != null) c.setPorts(normalizePorts(params.ports()));
			if (params.volumes() != null) c.setVolumes(params.volumes());
		}
		return repository.save(c);
	}

	@Override
	public void delete(UUID id) {
		repository.deleteById(id);
	}

	private List<Integer> normalizePorts(List<Integer> ports) {
		if (ports == null) return null;
		return ports.stream()
			.filter(p -> p != null && p > 0 && p <= 65535)
			.distinct()
			.sorted()
			.toList();
	}
}


