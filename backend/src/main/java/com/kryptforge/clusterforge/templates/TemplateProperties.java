package com.kryptforge.clusterforge.templates;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TemplateProperties {

	@Value("${docker.templates.path:}")
	private String templatesPath;

	@Value("${docker.volumes.basePath:}")
	private String volumesBasePath;

	public String getTemplatesPath() {
		return templatesPath;
	}

	public void setTemplatesPath(String templatesPath) {
		this.templatesPath = templatesPath;
	}

	public String getVolumesBasePath() {
		return volumesBasePath;
	}

	public void setVolumesBasePath(String volumesBasePath) {
		this.volumesBasePath = volumesBasePath;
	}
}


