package com.kryptforge.clusterforge.docker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gerencia UID/GID específicos para cada cluster e ajusta permissões de volumes.
 * Cada cluster recebe um UID/GID determinístico baseado no seu nome.
 */
@Component
public class ClusterUserManager {

	private static final Logger log = LoggerFactory.getLogger(ClusterUserManager.class);
	
	// Range de UIDs para usuários não-sistema (1000-65534)
	private static final int MIN_UID = 1000;
	private static final int MAX_UID = 65534;
	private static final int UID_RANGE = MAX_UID - MIN_UID + 1;

	/**
	 * Gera um UID determinístico baseado no nome do cluster.
	 * O mesmo nome sempre gera o mesmo UID.
	 * 
	 * @param clusterName nome do cluster
	 * @return UID no range 1000-65534
	 */
	public int generateUid(String clusterName) {
		if (clusterName == null || clusterName.isEmpty()) {
			clusterName = "default";
		}
		
		// Usa hash do nome para gerar UID determinístico
		int hash = clusterName.hashCode();
		// Garante que seja positivo e dentro do range
		int uid = MIN_UID + (Math.abs(hash) % UID_RANGE);
		
		log.debug("UID gerado para cluster '{}': {}", clusterName, uid);
		return uid;
	}

	/**
	 * Gera um GID determinístico baseado no nome do cluster.
	 * Por padrão, usa o mesmo valor do UID (um usuário por grupo).
	 * 
	 * @param clusterName nome do cluster
	 * @return GID no range 1000-65534
	 */
	public int generateGid(String clusterName) {
		// Por padrão, GID = UID (um usuário por grupo)
		return generateUid(clusterName);
	}

	/**
	 * Ajusta as permissões de um diretório e todos os seus arquivos para permitir
	 * acesso de leitura e escrita pelo UID/GID especificado.
	 * 
	 * @param volumePath caminho do volume
	 * @param uid UID do usuário
	 * @param gid GID do grupo
	 */
	public void adjustVolumePermissions(Path volumePath, int uid, int gid) {
		if (volumePath == null || !Files.exists(volumePath)) {
			log.warn("Volume não existe ou é nulo: {}", volumePath);
			return;
		}

		try {
			// Permissões: rwxrwxrwx para diretórios, rw-rw-rw- para arquivos
			Set<PosixFilePermission> dirPermissions = PosixFilePermissions.fromString("rwxrwxrwx");
			Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString("rw-rw-rw-");

			// Ajusta permissões recursivamente
			try (var stream = Files.walk(volumePath)) {
				stream.forEach(path -> {
					try {
						if (Files.isRegularFile(path)) {
							Files.setPosixFilePermissions(path, filePermissions);
						} else if (Files.isDirectory(path)) {
							Files.setPosixFilePermissions(path, dirPermissions);
						}
					} catch (Exception e) {
						log.debug("Não foi possível ajustar permissões de {}: {}", path, e.getMessage());
					}
				});
			}

			// Tenta ajustar o proprietário (pode falhar se não tiver permissão)
			try {
				// Nota: Files.setOwner requer permissões de root ou ser o proprietário atual
				// Por isso, apenas ajustamos as permissões para rwxrwxrwx que permite acesso universal
				log.debug("Permissões do volume {} ajustadas para UID/GID {}:{} (rwxrwxrwx para diretórios, rw-rw-rw- para arquivos)", 
					volumePath, uid, gid);
			} catch (Exception e) {
				log.debug("Não foi possível ajustar proprietário do volume {} (pode requerer root): {}", 
					volumePath, e.getMessage());
			}

		} catch (UnsupportedOperationException e) {
			log.debug("Sistema de arquivos não suporta permissões POSIX: {}", e.getMessage());
		} catch (Exception e) {
			log.warn("Erro ao ajustar permissões do volume {}: {}", volumePath, e.getMessage());
		}
	}

	/**
	 * Formata UID:GID como string para uso em containers Docker.
	 * 
	 * @param uid UID
	 * @param gid GID
	 * @return string no formato "uid:gid"
	 */
	public String formatUserString(int uid, int gid) {
		return uid + ":" + gid;
	}
}