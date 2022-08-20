/*
 * Copyright 2012-2021 the original author or authors.
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

package org.springframework.boot.build;

import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.gradle.api.Project;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.VariantVersionMappingStrategy;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomDeveloperSpec;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicenseSpec;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

/**
 *
 * 对于默认的约定 来说, 这里会创建一个 deploymentRepository 属性的仓库地址 ....(例如在build-project.sh中都通过命令行的形式指定了仓库的地址) ...
 * 所以一旦有这个属性,那么我们设定这样的一个仓库 ...
 * 并且 为这个项目创建MavenPublish 用于发布 ...
 * Conventions that are applied in the presence of the {@link MavenPublishPlugin}. When
 * the plugin is applied:
 *
 * <ul>
 * <li>If the {@code deploymentRepository} property has been set, a
 * {@link MavenArtifactRepository Maven artifact repository} is configured to publish to
 * it.
 * <li>The poms of all {@link MavenPublication Maven publications} are customized to meet
 * Maven Central's requirements.
 * <li>If the {@link JavaPlugin Java plugin} has also been applied:
 * <ul>
 * <li>Creation of Javadoc and source jars is enabled.
 * <li>Publication metadata (poms and Gradle module metadata) is configured to use
 * resolved versions.
 * </ul>
 * </ul>
 *
 * @author Andy Wilkinson
 * @author Christoph Dreis
 * @author Mike Smithson
 */
class MavenPublishingConventions {

	void apply(Project project) {
		project.getPlugins().withType(MavenPublishPlugin.class).all((mavenPublish) -> {
			PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
			if (project.hasProperty("deploymentRepository")) {
				publishing.getRepositories().maven((mavenRepository) -> {
					mavenRepository.setUrl(project.property("deploymentRepository"));
					mavenRepository.setName("deployment");
				});
			}
			publishing.getPublications().withType(MavenPublication.class)
					.all((mavenPublication) -> customizeMavenPublication(mavenPublication, project));
			project.getPlugins().withType(JavaPlugin.class).all((javaPlugin) -> {
				JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
				extension.withJavadocJar();
				extension.withSourcesJar();
			});
		});
	}

	private void customizeMavenPublication(MavenPublication publication, Project project) {
		customizePom(publication.getPom(), project);


		project.getPlugins().withType(JavaPlugin.class)
				.all((javaPlugin) -> customizeJavaMavenPublication(publication, project));

		// 压制Maven 可选特性的警告 ...
		suppressMavenOptionalFeatureWarnings(publication);
	}

	private void customizePom(MavenPom pom, Project project) {
		pom.getUrl().set("https://spring.io/projects/spring-boot");
		pom.getName().set(project.provider(project::getName));
		pom.getDescription().set(project.provider(project::getDescription));
		if (!isUserInherited(project)) {
			pom.organization(this::customizeOrganization);
		}
		pom.licenses(this::customizeLicences);
		pom.developers(this::customizeDevelopers);
		pom.scm((scm) -> customizeScm(scm, project));
		if (!isUserInherited(project)) {
			pom.issueManagement(this::customizeIssueManagement);
		}
	}

	// 定制Maven 发布,它如何控制版本 ..
	private void customizeJavaMavenPublication(MavenPublication publication, Project project) {
		addMavenOptionalFeature(project);

		publication.versionMapping((strategy) -> strategy.usage(Usage.JAVA_API, (mappingStrategy) -> mappingStrategy
				.fromResolutionOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)));
		publication.versionMapping(
				(strategy) -> strategy.usage(Usage.JAVA_RUNTIME, VariantVersionMappingStrategy::fromResolutionResult));
	}

	/**
	 * 增加一个可选的特性 - 允许maven 插件去声明 可选的依赖出现在POM 中,这在Eclipse中愉快的使用m2e 是必要的 ...
	 * Add a feature that allows maven plugins to declare optional dependencies that
	 * appear in the POM. This is required to make m2e in Eclipse happy.
	 * @param project the project to add the feature to
	 */
	private void addMavenOptionalFeature(Project project) {
		// 约定 和  扩展都是一样的 ...
		// 都添加了一些 脚本块...
		JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
		JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);

		// 为扩展注册一个能力, 让这个特性使用资源集 (main) ...
		extension.registerFeature("mavenOptional",
				(feature) -> feature.usingSourceSet(convention.getSourceSets().getByName("main")));

		// 具有变体的临时组件 .. 通过项目获取组件
		// 目前我已知晓的 组件也就是 components.java / javaplatform
		// 分别由java 插件提供   / java 平台插件提供 ...
		AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents()
				.findByName("java");

//		从配置增加变种
		// 盲猜 configuration 最终要映射为对应的元素 ...
		javaComponent.addVariantsFromConfiguration(
				project.getConfigurations().findByName("mavenOptionalRuntimeElements"),
				ConfigurationVariantDetails::mapToOptional);
	}

	private void suppressMavenOptionalFeatureWarnings(MavenPublication publication) {
		publication.suppressPomMetadataWarningsFor("mavenOptionalApiElements");
		publication.suppressPomMetadataWarningsFor("mavenOptionalRuntimeElements");
	}

	private void customizeOrganization(MavenPomOrganization organization) {
		organization.getName().set("Pivotal Software, Inc.");
		organization.getUrl().set("https://spring.io");
	}

	private void customizeLicences(MavenPomLicenseSpec licences) {
		licences.license((licence) -> {
			licence.getName().set("Apache License, Version 2.0");
			licence.getUrl().set("https://www.apache.org/licenses/LICENSE-2.0");
		});
	}

	private void customizeDevelopers(MavenPomDeveloperSpec developers) {
		developers.developer((developer) -> {
			developer.getName().set("Pivotal");
			developer.getEmail().set("info@pivotal.io");
			developer.getOrganization().set("Pivotal Software, Inc.");
			developer.getOrganizationUrl().set("https://www.spring.io");
		});
	}

	private void customizeScm(MavenPomScm scm, Project project) {
		if (!isUserInherited(project)) {
			scm.getConnection().set("scm:git:git://github.com/spring-projects/spring-boot.git");
			scm.getDeveloperConnection().set("scm:git:ssh://git@github.com/spring-projects/spring-boot.git");
		}
		scm.getUrl().set("https://github.com/spring-projects/spring-boot");
	}

	private void customizeIssueManagement(MavenPomIssueManagement issueManagement) {
		issueManagement.getSystem().set("GitHub");
		issueManagement.getUrl().set("https://github.com/spring-projects/spring-boot/issues");
	}

	private boolean isUserInherited(Project project) {
		return "spring-boot-starter-parent".equals(project.getName())
				|| "spring-boot-dependencies".equals(project.getName());
	}

}
