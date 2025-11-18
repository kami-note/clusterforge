package com.kryptforge.clusterforge.webdav;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebDavProperties {

	@Value("${clusterforge.webdav.image:ghcr.io/hacdias/webdav:latest}")
	private String image;

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}
}



