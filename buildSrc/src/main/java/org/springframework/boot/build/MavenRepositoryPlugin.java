/*
 * Copyright 2012-2020 the original author or authors.
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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlatformPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

/**
 * A plugin to make a project's {@code deployment} publication available as a Maven
 * repository. The repository can be consumed by depending upon the project using the
 * {@code mavenRepository} configuration.
 *
 * 将项目打包发布到可用的maven仓库的地址 ...
 * 这个仓库 能够使用在项目之上使用maven Repository 配置消费 ..
 *
 * @author Andy Wilkinson
 */
public class MavenRepositoryPlugin implements Plugin<Project> {

	/**
	 * Name of the {@code mavenRepository} configuration.
	 *
	 * maven 仓库配置 ...
	 */
	public static final String MAVEN_REPOSITORY_CONFIGURATION_NAME = "mavenRepository";

	/**
	 * Name of the task that publishes to the project repository.
	 * 发布到项目仓库地址的任务名称 ..
	 *
	 * 设定了 PubName 等于 Maven, 于是PubName 对应了每一个publication(声明在publishing 的publications中的每一个发布物) ...
	 * 仓库为 RepoName 等于 Project ...
	 *
	 * 这里针对DeployedPlugin 做的定制 ..
	 */
	public static final String PUBLISH_TO_PROJECT_REPOSITORY_TASK_NAME = "publishMavenPublicationToProjectRepository";

	@Override
	public void apply(Project project) {
		project.getPlugins().apply(MavenPublishPlugin.class);
		// 获取Publishing扩展 ..
		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
		// 获取maven-repository 仓库地址 ...
		File repositoryLocation = new File(project.getBuildDir(), "maven-repository");
		// 然后根据设定的仓库地址, 执行maven 方法 ..
		// 通过获取仓库地址容器  增加一个本地指定的仓库名称  ...
		// name 为 project ...
		publishing.getRepositories().maven((mavenRepository) -> {
			mavenRepository.setName("project");
			mavenRepository.setUrl(repositoryLocation.toURI());
		});
		// 获取任务,尝试匹配
		// matching 会导致所有的任务实例化(查看https://docs.gradle.org/current/userguide/task_configuration_avoidance.html#Existing vs New API overview 了解更多细节)
		project.getTasks().matching((task) -> task.getName().equals(PUBLISH_TO_PROJECT_REPOSITORY_TASK_NAME))
				.all((task) -> setUpProjectRepository(project, task, repositoryLocation));

		// 这个任务 仅仅在 Gradle 内部的 MavenPluginPublishPlugin 才拥有
		project.getTasks().matching((task) -> task.getName().equals("publishPluginMavenPublicationToProjectRepository"))
				.all((task) -> setUpProjectRepository(project, task, repositoryLocation));
	}


	// 设置上级项目仓库 ...
	private void setUpProjectRepository(Project project, Task publishTask, File repositoryLocation) {
		// 构建阶段 -> 增加任务到任务列表 执行前,清理仓库地址 ...
		publishTask.doFirst(new CleanAction(repositoryLocation));

		// 根据项目的配置 创建一个新的 configuration ...
		Configuration projectRepository = project.getConfigurations().create(MAVEN_REPOSITORY_CONFIGURATION_NAME);
		//设置项目的工件 ... 增加一个 ... 此工件由publishTask 构建  ...
		// 将给定的配置应用到 工件上 ...,并且工件的信息由给定的repositoryLocation 对应的文件进行处理 ..
		project.getArtifacts().add(projectRepository.getName(), repositoryLocation,
				(artifact) -> artifact.builtBy(publishTask));

		// 获取配置的依赖信息 ..
		// 这些就是依赖集合 ...(maven 坐标形式的数据)
		DependencySet target = projectRepository.getDependencies();
		// 获取java 插件 并且 将所有依赖增加 ...
		project.getPlugins().withType(JavaPlugin.class).all((javaPlugin) -> addMavenRepositoryDependencies(project,
				JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, target));
		project.getPlugins().withType(JavaLibraryPlugin.class)
				.all((javaLibraryPlugin) -> addMavenRepositoryDependencies(project, JavaPlugin.API_CONFIGURATION_NAME,
						target));
		project.getPlugins().withType(JavaPlatformPlugin.class)
				.all((javaPlugin) -> addMavenRepositoryDependencies(project, JavaPlatformPlugin.API_CONFIGURATION_NAME,
						target));
	}

	// 增加 maven 仓库 依赖到指定的项目上 ..
	private void addMavenRepositoryDependencies(Project project, String sourceConfigurationName, DependencySet target) {
		project.getConfigurations().getByName(sourceConfigurationName).getDependencies()
				.withType(ProjectDependency.class).all((dependency) -> {
					// 依赖描述符
					Map<String, String> dependencyDescriptor = new HashMap<>();
					dependencyDescriptor.put("path", dependency.getDependencyProject().getPath());
					dependencyDescriptor.put("configuration", MAVEN_REPOSITORY_CONFIGURATION_NAME);
					target.add(project.getDependencies().project(dependencyDescriptor));
				});
	}

	/**
	 * 清理动作 ..
	 */
	private static final class CleanAction implements Action<Task> {

		private final File location;

		private CleanAction(File location) {
			this.location = location;
		}

		@Override
		public void execute(Task task) {
			task.getProject().delete(this.location);
		}

	}

}
