/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.start.site.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.spring.initializr.generator.version.Version;
import io.spring.initializr.generator.version.VersionParser;
import io.spring.initializr.metadata.DefaultMetadataElement;
import io.spring.initializr.web.support.InitializrMetadataUpdateStrategy;
import io.spring.initializr.web.support.SaganInitializrMetadataUpdateStrategy;
import jodd.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * An {@link InitializrMetadataUpdateStrategy} that performs additional filtering of
 * versions available on spring.io.
 *
 * @author Stephane Nicoll
 */
@Slf4j
public class StartInitializrMetadataUpdateStrategy extends SaganInitializrMetadataUpdateStrategy {

	private static final Comparator<DefaultMetadataElement> VERSION_METADATA_ELEMENT_COMPARATOR = new VersionMetadataElementComparator();

	private final ObjectMapper objectMapper;

	public StartInitializrMetadataUpdateStrategy(RestTemplate restTemplate, ObjectMapper objectMapper) {
		super(restTemplate, objectMapper);
		this.objectMapper = objectMapper;
	}

	@Override
	protected List<DefaultMetadataElement> fetchSpringBootVersions(String url) {
		log.info("fetchSpringBootVersions: {}, changed to read from json file", url);
		URL resource = this.getClass().getResource("/spring/spring-version.json");
		if (resource == null) {
			return new ArrayList<>();
		}
		String path = resource.getPath();
		try {
			String springVersionJson = FileUtil.readString(path);
			JsonNode content = objectMapper.readTree(springVersionJson);
			log.info("spring version content: {}", content.toPrettyString());
			return getBootVersion(content);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<DefaultMetadataElement> getBootVersion(JsonNode content) {
		ArrayNode releases = (ArrayNode) content.get("projectReleases");
		List<DefaultMetadataElement> list = new ArrayList<>();

		for (JsonNode node : releases) {
			DefaultMetadataElement versionMetadata = this.parseVersionMetadata(node);
			if (versionMetadata != null) {
				list.add(versionMetadata);
			}
		}

		list.sort(VERSION_METADATA_ELEMENT_COMPARATOR.reversed());
		return list;
	}

	private DefaultMetadataElement parseVersionMetadata(JsonNode node) {
		String versionId = node.get("version").textValue();
		Version version = VersionParser.DEFAULT.safeParse(versionId);
		if (version == null) {
			return null;
		}
		else {
			DefaultMetadataElement versionMetadata = new DefaultMetadataElement();
			versionMetadata.setId(versionId);
			versionMetadata.setName(this.determineDisplayName(version));
			versionMetadata.setDefault(node.get("current").booleanValue());
			return versionMetadata;
		}
	}

	private String determineDisplayName(Version version) {
		StringBuilder sb = new StringBuilder();
		sb.append(version.getMajor()).append(".").append(version.getMinor()).append(".").append(version.getPatch());
		if (version.getQualifier() != null) {
			sb.append(this.determineSuffix(version.getQualifier()));
		}

		return sb.toString();
	}

	private static class VersionMetadataElementComparator implements Comparator<DefaultMetadataElement> {

		private static final VersionParser versionParser;

		private VersionMetadataElementComparator() {
		}

		public int compare(DefaultMetadataElement o1, DefaultMetadataElement o2) {
			Version o1Version = versionParser.parse(o1.getId());
			Version o2Version = versionParser.parse(o2.getId());
			return o1Version.compareTo(o2Version);
		}

		static {
			versionParser = VersionParser.DEFAULT;
		}

	}

	private String determineSuffix(Version.Qualifier qualifier) {
		String id = qualifier.getId();
		if (id.equals("RELEASE")) {
			return "";
		}
		else {
			StringBuilder sb = new StringBuilder(" (");
			if (id.contains("SNAPSHOT")) {
				sb.append("SNAPSHOT");
			}
			else {
				sb.append(id);
				if (qualifier.getVersion() != null) {
					sb.append(qualifier.getVersion());
				}
			}

			sb.append(")");
			return sb.toString();
		}
	}

}
