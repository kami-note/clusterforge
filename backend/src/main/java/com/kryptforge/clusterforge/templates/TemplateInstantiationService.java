package com.kryptforge.clusterforge.templates;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kryptforge.clusterforge.ftp.FtpService.FtpServerInfo;
import com.kryptforge.clusterforge.templates.processing.AuxiliaryServicesSetup;
import com.kryptforge.clusterforge.templates.processing.AuxiliaryServicesSetup.AuxiliaryServicesResult;
import com.kryptforge.clusterforge.templates.processing.ContainerCreator;
import com.kryptforge.clusterforge.templates.processing.EnvironmentProcessor;
import com.kryptforge.clusterforge.templates.processing.PortProcessor;
import com.kryptforge.clusterforge.templates.processing.PortProcessor.PortMappingResult;
import com.kryptforge.clusterforge.templates.processing.TemplateReader;
import com.kryptforge.clusterforge.templates.processing.TemplateReader.ComposeServiceSpec;
import com.kryptforge.clusterforge.templates.processing.VolumeProcessor;
import com.kryptforge.clusterforge.webdav.WebDavService.WebDavServerInfo;

/**
 * Serviço orquestrador para instanciação de templates Docker Compose.
 * 
 * <p>
 * Refatorado para seguir o princípio de responsabilidade única.
 * Delega para serviços especializados:
 * </p>
 * <ul>
 * <li>{@link TemplateReader} - Leitura e parsing de docker-compose.yml</li>
 * <li>{@link EnvironmentProcessor} - Processamento de variáveis de
 * ambiente</li>
 * <li>{@link PortProcessor} - Processamento e alocação de portas</li>
 * <li>{@link VolumeProcessor} - Processamento de volumes e binds</li>
 * <li>{@link ContainerCreator} - Criação e inicialização de containers</li>
 * <li>{@link AuxiliaryServicesSetup} - Configuração de FTP e WebDAV</li>
 * </ul>
 * 
 * <p>
 * Reduzido de ~738 linhas para ~150 linhas.
 * </p>
 */
@Service
public class TemplateInstantiationService {

	private static final Logger log = LoggerFactory.getLogger(TemplateInstantiationService.class);

	private final TemplateReader templateReader;
	private final EnvironmentProcessor environmentProcessor;
	private final PortProcessor portProcessor;
	private final VolumeProcessor volumeProcessor;
	private final ContainerCreator containerCreator;
	private final AuxiliaryServicesSetup auxiliaryServicesSetup;

	public TemplateInstantiationService(
			TemplateReader templateReader,
			EnvironmentProcessor environmentProcessor,
			PortProcessor portProcessor,
			VolumeProcessor volumeProcessor,
			ContainerCreator containerCreator,
			AuxiliaryServicesSetup auxiliaryServicesSetup) {
		this.templateReader = Objects.requireNonNull(templateReader, "templateReader");
		this.environmentProcessor = Objects.requireNonNull(environmentProcessor, "environmentProcessor");
		this.portProcessor = Objects.requireNonNull(portProcessor, "portProcessor");
		this.volumeProcessor = Objects.requireNonNull(volumeProcessor, "volumeProcessor");
		this.containerCreator = Objects.requireNonNull(containerCreator, "containerCreator");
		this.auxiliaryServicesSetup = Objects.requireNonNull(auxiliaryServicesSetup, "auxiliaryServicesSetup");
	}

	/**
	 * Instancia um template Docker Compose criando um container único.
	 * 
	 * <p>
	 * Pipeline de instanciação:
	 * </p>
	 * <ol>
	 * <li>Validar requisição</li>
	 * <li>Ler template e especificação do compose</li>
	 * <li>Processar variáveis de ambiente</li>
	 * <li>Processar e alocar portas</li>
	 * <li>Processar volumes e criar volume da instância</li>
	 * <li>Fazer pull da imagem e criar container</li>
	 * <li>Configurar servidores auxiliares (FTP e WebDAV)</li>
	 * </ol>
	 * 
	 * @param templateName    nome do template
	 * @param instanceName    nome da instância
	 * @param overrideEnv     variáveis de ambiente de override
	 * @param overridePorts   portas de override
	 * @param overrideBinds   binds de override
	 * @param cpuLimitPercent limite de CPU (1-100)
	 * @param memoryLimitMb   limite de memória em MB
	 * @return resultado da instanciação
	 * @throws IOException se erro na leitura do template
	 */
	public InstantiationResult instantiate(
			String templateName,
			String instanceName,
			Map<String, String> overrideEnv,
			List<String> overridePorts,
			List<String> overrideBinds,
			Integer cpuLimitPercent,
			Long memoryLimitMb) throws IOException {

		log.info("Iniciando instanciação do template '{}' como '{}'", templateName, instanceName);

		// 1. Validar requisição
		templateReader.validateRequest(templateName, instanceName);

		// 2. Ler template e especificação
		Path templateDir = templateReader.resolveTemplateDirectory(templateName);
		ComposeServiceSpec spec = templateReader.readFirstService(templateDir);
		log.debug("Template '{}' lido com sucesso. Imagem: {}", templateName, spec.image);

		// 3. Processar variáveis de ambiente
		Map<String, String> env = environmentProcessor.mergeEnvironment(spec.environment, overrideEnv);
		log.debug("Variáveis de ambiente processadas: {} variáveis", env.size());

		// 4. Processar e alocar portas
		PortMappingResult portResult = portProcessor.processPortMappings(templateName, spec.ports, overridePorts);
		log.debug("Portas processadas: {} mapeamentos", portResult.mappedPorts().size());

		// 5. Processar volumes
		List<String> binds = volumeProcessor.processVolumeBindings(spec.volumes, overrideBinds, templateDir);
		String mainVolumePath = volumeProcessor.identifyOrCreateMainVolume(instanceName, binds);
		String instanceVolumePath = volumeProcessor.ensureInstanceSpecificVolume(instanceName, mainVolumePath,
				templateDir);
		List<String> instanceBinds = volumeProcessor.updateBindsForInstance(binds, mainVolumePath, instanceVolumePath);
		log.debug("Volumes processados. Volume da instância: {}", instanceVolumePath);

		// 6. Pull da imagem e criar container
		containerCreator.pullImageSafely(spec.image, templateName);
		String containerId = containerCreator.createAndStartContainer(
				spec, env, portResult.mappedPorts(), instanceBinds, instanceName,
				cpuLimitPercent, memoryLimitMb, portResult.allocatedHostPorts(), templateName);
		log.info("Container '{}' criado com ID: {}", instanceName, containerId);

		// 7. Configurar servidores auxiliares
		AuxiliaryServicesResult auxiliaryResult = auxiliaryServicesSetup.setupAuxiliaryServices(instanceName,
				instanceVolumePath);
		FtpServerInfo ftpInfo = auxiliaryResult.ftpInfo();
		WebDavServerInfo webDavInfo = auxiliaryResult.webDavInfo();

		if (ftpInfo != null) {
			log.info("FTP configurado para '{}' na porta {}", instanceName, ftpInfo.hostPort());
		}
		if (webDavInfo != null) {
			log.info("WebDAV configurado para '{}' na porta {}", instanceName, webDavInfo.hostPort());
		}

		log.info("Instanciação do template '{}' como '{}' concluída com sucesso", templateName, instanceName);

		return new InstantiationResult(containerId, portResult.mappedPorts(), ftpInfo, webDavInfo);
	}
}
