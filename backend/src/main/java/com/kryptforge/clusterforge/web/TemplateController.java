package com.kryptforge.clusterforge.web;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.github.dockerjava.api.model.Container;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.clusters.ClusterService;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.InstantiationResult;
import com.kryptforge.clusterforge.templates.TemplateInstantiationService;
import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateRequest;
import com.kryptforge.clusterforge.templates.dto.TemplateInstantiateResponse;
import com.kryptforge.clusterforge.templates.dto.TemplateSummary;

import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/api/templates", produces = MediaType.APPLICATION_JSON_VALUE)
public class TemplateController {

	private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

	private final TemplateService templateService;
	private final TemplateInstantiationService instantiationService;
	private final DockerEngineService dockerEngineService;
	private final ClusterService clusterService;
	private final ClusterRepository clusterRepository;

	public TemplateController(TemplateService templateService, TemplateInstantiationService instantiationService, DockerEngineService dockerEngineService, ClusterService clusterService, ClusterRepository clusterRepository) {
		this.templateService = templateService;
		this.instantiationService = instantiationService;
		this.dockerEngineService = dockerEngineService;
		this.clusterService = clusterService;
		this.clusterRepository = clusterRepository;
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
		@Valid @RequestBody TemplateInstantiateRequest request
	) {
		try {
			// verifica conflito de nome de container
			String expected = "/" + request.name();
			boolean inUse = dockerEngineService.listContainers(true).stream()
				.map(Container::getNames)
				.filter(names -> names != null)
				.anyMatch(names -> java.util.Arrays.asList(names).contains(expected));
			if (inUse) {
				return ResponseEntity.status(HttpStatus.CONFLICT).build();
			}

			// verifica se já existe instância com esse nome no banco
			// (a verificação de conflito de nome já é feita pelo ClusterService.create)

			// instancia o template e cria o container
			InstantiationResult result = instantiationService.instantiate(
				name,
				request.name(),
				request.env(),
				request.ports(),
				request.binds(),
				request.cpuLimitPercent(),
				request.memoryLimitMb()
			);

			// extrai portas do host das portas mapeadas (formato "hostPort:containerPort")
			List<Integer> hostPorts = new ArrayList<>();
			if (result.mappedPorts() != null) {
				for (String portMapping : result.mappedPorts()) {
					String[] parts = portMapping.split(":");
					if (parts.length >= 1) {
						try {
							hostPorts.add(Integer.parseInt(parts[0].trim()));
						} catch (NumberFormatException e) {
							log.debug("Formato de porta inválido ignorado: {}", portMapping);
						}
					}
				}
			}

			// persiste a instância no banco de dados (com status PENDING inicialmente)
			ClusterInstance instance = clusterService.create(
				request.name(),
				name,
				new ClusterService.ClusterParams(
					request.env(),
					hostPorts,
					request.binds(),
					request.cpuLimitPercent(),
					request.memoryLimitMb(),
					null,
					null
				)
			);

			// atualiza com containerId e status ACTIVE após criação bem-sucedida
			// Nota: atualizamos diretamente via repositório durante a instanciação
			// pois esses campos são apenas para uso interno e não devem ser editáveis via API
			instance.setContainerId(result.containerId());
			instance = clusterRepository.save(instance);
			instance = clusterService.updateStatus(instance.getId(), ClusterStatus.ACTIVE);

			// atualiza com informações do servidor FTP se foi criado
			if (result.ftpInfo() != null) {
				instance.setFtpContainerId(result.ftpInfo().containerId());
				instance.setFtpPort(result.ftpInfo().hostPort());
				instance.setFtpUser(result.ftpInfo().ftpUser());
				instance.setFtpPassword(result.ftpInfo().ftpPassword());
				instance = clusterRepository.save(instance);
			}

			if (result.webDavInfo() != null) {
				instance.setWebDavContainerId(result.webDavInfo().containerId());
				instance.setWebDavPort(result.webDavInfo().hostPort());
				instance.setWebDavUser(result.webDavInfo().username());
				instance.setWebDavPassword(result.webDavInfo().password());
				instance = clusterRepository.save(instance);
			}

			return ResponseEntity.status(HttpStatus.CREATED)
				.body(new TemplateInstantiateResponse(result.containerId(), request.name()));
		} catch (IllegalArgumentException e) {
			markInstanceAsErrorIfExists(request.name());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		} catch (NoSuchFileException e) {
			markInstanceAsErrorIfExists(request.name());
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
		} catch (Exception e) {
			markInstanceAsErrorIfExists(request.name());
			if (e instanceof IOException) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao instanciar template", e);
			}
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado: " + e.getMessage(), e);
		}
	}

	/**
	 * Marca uma instância como ERROR se ela existir no banco.
	 * Usado para cleanup em caso de falha durante a instanciação.
	 * 
	 * @param instanceName Nome da instância a marcar como erro
	 */
	private void markInstanceAsErrorIfExists(String instanceName) {
		try {
			clusterService.list().stream()
				.filter(c -> instanceName.equals(c.getName()))
				.findFirst()
				.ifPresent(instance -> {
					log.warn("Marcando instância '{}' como ERROR devido a falha na instanciação", instanceName);
					clusterService.updateStatus(instance.getId(), ClusterStatus.ERROR);
				});
		} catch (Exception e) {
			log.warn("Falha ao marcar instância '{}' como ERROR: {}", instanceName, e.getMessage());
		}
	}
}


