package com.kryptforge.clusterforge.templates.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kryptforge.clusterforge.ftp.FtpService;
import com.kryptforge.clusterforge.ftp.FtpService.FtpServerInfo;
import com.kryptforge.clusterforge.webdav.WebDavService;
import com.kryptforge.clusterforge.webdav.WebDavService.WebDavServerInfo;

/**
 * Responsável por configurar servidores auxiliares (FTP e WebDAV).
 * 
 * <p>
 * Extraído do TemplateInstantiationService para seguir SRP.
 * </p>
 */
@Component
public class AuxiliaryServicesSetup {

    private static final Logger log = LoggerFactory.getLogger(AuxiliaryServicesSetup.class);

    private final FtpService ftpService;
    private final WebDavService webDavService;
    private final PortProcessor portProcessor;

    public AuxiliaryServicesSetup(
            FtpService ftpService,
            WebDavService webDavService,
            PortProcessor portProcessor) {
        this.ftpService = ftpService;
        this.webDavService = webDavService;
        this.portProcessor = portProcessor;
    }

    /**
     * Resultado da configuração de serviços auxiliares.
     */
    public record AuxiliaryServicesResult(
            FtpServerInfo ftpInfo,
            WebDavServerInfo webDavInfo) {
    }

    /**
     * Configura servidores auxiliares (FTP e WebDAV) para uma instância.
     * 
     * @param instanceName nome da instância
     * @param volumePath   caminho do volume
     * @return resultado com informações dos servidores
     */
    public AuxiliaryServicesResult setupAuxiliaryServices(String instanceName, String volumePath) {
        FtpServerInfo ftpInfo = setupFtpServer(instanceName, volumePath);
        WebDavServerInfo webDavInfo = setupWebDavServer(instanceName, volumePath, ftpInfo);

        return new AuxiliaryServicesResult(ftpInfo, webDavInfo);
    }

    /**
     * Configura servidor FTP para a instância.
     * 
     * @param instanceName nome da instância
     * @param volumePath   caminho do volume
     * @return informações do servidor FTP ou null se falhar
     */
    public FtpServerInfo setupFtpServer(String instanceName, String volumePath) {
        int ftpPort = -1;

        try {
            ftpPort = portProcessor.allocatePort();

            if (ftpPort == -1) {
                log.warn("Não foi possível alocar porta para servidor FTP do container {}", instanceName);
                return null;
            }

            log.info("Criando servidor FTP para container {} na porta {}", instanceName, ftpPort);
            FtpServerInfo ftpInfo = ftpService.createFtpServer(instanceName, volumePath, ftpPort, null, null);
            log.info("Servidor FTP criado com sucesso para container {}: containerId={}, port={}",
                    instanceName, ftpInfo.containerId(), ftpInfo.hostPort());

            return ftpInfo;

        } catch (Exception e) {
            log.error("Falha ao criar servidor FTP para container {}: {}", instanceName, e.getMessage(), e);

            if (ftpPort != -1) {
                portProcessor.releasePortSafely(ftpPort);
            }

            return null;
        }
    }

    /**
     * Configura servidor WebDAV para a instância.
     * 
     * @param instanceName nome da instância
     * @param volumePath   caminho do volume
     * @param ftpInfo      informações do servidor FTP (para credenciais)
     * @return informações do servidor WebDAV ou null se falhar
     */
    public WebDavServerInfo setupWebDavServer(
            String instanceName,
            String volumePath,
            FtpServerInfo ftpInfo) {

        if (ftpInfo == null) {
            return null;
        }

        int webDavPort = -1;

        try {
            webDavPort = portProcessor.allocatePort();

            if (webDavPort == -1) {
                log.warn("Não foi possível alocar porta para WebDAV de {}", instanceName);
                return null;
            }

            log.info("Criando servidor WebDAV para container {} na porta {} com volume {}",
                    instanceName, webDavPort, volumePath);

            return webDavService.createWebDavServer(
                    instanceName,
                    volumePath,
                    webDavPort,
                    ftpInfo.ftpUser(),
                    ftpInfo.ftpPassword());

        } catch (Exception e) {
            log.error("Falha ao criar servidor WebDAV para container {}: {}", instanceName, e.getMessage(), e);

            if (webDavPort != -1) {
                portProcessor.releasePortSafely(webDavPort);
            }

            return null;
        }
    }
}
