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

package org.springframework.boot.build.optional;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Spring 自己的一个 用来对Maven 风格的可选依赖的增加支持 .
 * 创建一个可选的optional 配置 ..
 * 这个配置是作为项目的编译和运行时类路径的一部分,但是它不会影响到依赖项目的类路径 ...
 *
 * A {@code Plugin} that adds support for Maven-style optional dependencies. Creates a new
 * {@code optional} configuration. The {@code optional} configuration is part of the
 * project's compile and runtime classpaths but does not affect the classpath of dependent
 * projects.
 *
 * @author Andy Wilkinson
 */
public class OptionalDependenciesPlugin implements Plugin<Project> {

	/**
	 * Name of the {@code optional} configuration.
	 */
	public static final String OPTIONAL_CONFIGURATION_NAME = "optional";

	@Override
	public void apply(Project project) {
		Configuration optional = project.getConfigurations().create("optional");
		// 这些具体含义后面了解 ..
		optional.setCanBeConsumed(false);
		optional.setCanBeResolved(false);

		// 在具有Java 插件的情况下, 获取约定 ...
		project.getPlugins().withType(JavaPlugin.class, (javaPlugin) -> {
			// https://www.yuque.com/gaolengdehulusi/nggog2/nbgs49#S1kWS
			// 本身Convention 在 7.x 版本开始不再建议使用 ... 应该使用扩展属性,但是也没事 ..还能用 ..
			// 拿到资源集容器 ..
			SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class)
					.getSourceSets();
			// 对每一个资源集进行配置 ...  让他们编译类路径配置继承于 optional ...
			sourceSets.all((sourceSet) -> {
				project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName())
						.extendsFrom(optional);
				// 包括运行时的时候 ..
				project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName())
						.extendsFrom(optional);
			});
		});
	}

}
