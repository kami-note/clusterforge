package com.kryptforge.clusterforge.templates;

import java.io.IOException;
import java.util.List;

import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateSummary;

public interface TemplateService {

	List<TemplateSummary> listTemplates() throws IOException;

	TemplateDetail getTemplate(String name) throws IOException;
}


