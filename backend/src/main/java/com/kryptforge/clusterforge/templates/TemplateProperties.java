package com.kryptforge.clusterforge.templates;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docker.templates")
public class TemplateProperties {

	private String path;
	private String templatesPath;
	private String volumesBasePath;

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	// Compat: usado por DefaultTemplateService
	public String getTemplatesPath() {
		return templatesPath != null ? templatesPath : path;
	}

	public void setTemplatesPath(String templatesPath) {
		this.templatesPath = templatesPath;
		// Mantém compatibilidade com 'path'
		this.path = templatesPath;
	}

	public String getVolumesBasePath() {
		return volumesBasePath;
	}

	public void setVolumesBasePath(String volumesBasePath) {
		this.volumesBasePath = volumesBasePath;
	}
}


