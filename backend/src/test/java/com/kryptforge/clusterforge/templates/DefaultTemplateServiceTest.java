package com.kryptforge.clusterforge.templates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateSummary;

class DefaultTemplateServiceTest {

	@TempDir
	Path tempDir;

	private DefaultTemplateService createServiceWithRoot(Path root) {
		TemplateProperties props = new TemplateProperties();
		props.setTemplatesPath(root.toString());
		props.setVolumesBasePath(root.resolve("volumes").toString());
		return new DefaultTemplateService(props);
	}

	@Test
	void listTemplates_filtersOnlyDirectoriesWithCompose() throws Exception {
        Path valid = tempDir.resolve("webserver-php");
        Path invalid = tempDir.resolve("no-compose");
        Files.createDirectories(valid);
        Files.createDirectories(invalid);
        Files.writeString(valid.resolve("docker-compose.yml"), "version: '3.9'\nservices:\n  php:\n    image: php:8.2-apache\n");
        Files.writeString(valid.resolve("metadata.json"), "{\"name\":\"webserver-php\"}");
        Files.writeString(invalid.resolve("README.md"), "no compose here");

        DefaultTemplateService service = createServiceWithRoot(tempDir);

        List<TemplateSummary> list = service.listTemplates();
        assertEquals(1, list.size());
        TemplateSummary s = list.get(0);
        assertEquals("webserver-php", s.name());
        assertTrue(s.hasMetadata());
	}

	@Test
	void getTemplate_returnsDetailWithFiles_andRequiresCompose() throws Exception {
        Path valid = tempDir.resolve("minecraft-java");
        Files.createDirectories(valid.resolve("nested"));
        Files.writeString(valid.resolve("docker-compose.yml"), "version: '3.9'\nservices:\n  mc:\n    image: eclipse-temurin:8\n");
        Files.writeString(valid.resolve("metadata.json"), "{\"name\":\"minecraft-java\",\"ports\":[25565]}");
        Files.writeString(valid.resolve("nested/file.txt"), "x");

        DefaultTemplateService service = createServiceWithRoot(tempDir);

        TemplateDetail detail = service.getTemplate("minecraft-java");
        assertEquals("minecraft-java", detail.name());
        assertTrue(detail.composePresent());
        assertNotNull(detail.files());
        assertTrue(detail.files().stream().anyMatch(f -> f.relativePath().endsWith("docker-compose.yml")));
        assertNotNull(detail.metadata());
        assertEquals("minecraft-java", detail.metadata().name());
	}

	@Test
	void getTemplate_throwsWhenTemplateMissingOrWithoutCompose() throws Exception {
        Path dir = tempDir.resolve("exists-no-compose");
        Files.createDirectories(dir);

        DefaultTemplateService service = createServiceWithRoot(tempDir);

        assertThrows(IOException.class, () -> service.getTemplate("not-exists"));
        assertThrows(IOException.class, () -> service.getTemplate("exists-no-compose"));
	}
}


