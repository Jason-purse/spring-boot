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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.springframework.boot.build.MavenRepositoryPlugin;

/**
 * A plugin applied to a project that should be deployed.
 * @author Andy Wilkinson
 */
public class DeployedPlugin implements Plugin<Project> {

	/**
	 * Name of the task that generates the deployed pom file.
	 */
	public static final String GENERATE_POM_TASK_NAME = "generatePomFileForMavenPublication";

	@Override
	public void apply(Project project) {
		project.getPlugins().apply(MavenPublishPlugin.class);
		project.getPlugins().apply(MavenRepositoryPlugin.class);

		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

		// 创建一个 maven Publication(指定发布规则) ...(包括打包的jar 叫什么名称) ...
		// 每一个发布,对应了一个发布物 ..
		MavenPublication mavenPublication = publishing.getPublications().create("maven", MavenPublication.class);
		project.afterEvaluate((evaluated) -> {
			project.getPlugins().withType(JavaPlugin.class).all((javaPlugin) -> {
				if (((Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)).isEnabled()) {
					// 详情 https://www.yuque.com/gaolengdehulusi/nggog2/nbgs49#YStSd
					// 详情 https://www.yuque.com/gaolengdehulusi/nggog2/nfbmzp#eUmQc
					project.getComponents().matching((component) -> component.getName().equals("java"))
							// 配置maven 发布  打包的组件来源 ...
							.all((javaComponent) -> mavenPublication.from(javaComponent));
				}
			});
		});


		// 由于 JavaPlugin 不可和平台插件混用,那么这里两者的互斥关系,应该在子项目中有所体现
		// 例如使用了JavaPlugin的项目不可能使用平台插件 ...
		// 首先了解 Java 平台插件到底是什么 ..
		// https://docs.gradle.org/current/userguide/java_platform_plugin.html#sec:java_platform_publishing
		project.getPlugins().withType(JavaPlatformPlugin.class)
				.all((javaPlugin) -> project.getComponents()
						.matching((component) -> component.getName().equals("javaPlatform"))
						.all((javaComponent) -> mavenPublication.from(javaComponent)));
	}

}
