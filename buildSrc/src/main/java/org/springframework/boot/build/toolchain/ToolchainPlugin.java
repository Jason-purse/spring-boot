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

package org.springframework.boot.build.toolchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

/**
 * Gradle 自定义工具链支持 ...
 * {@link Plugin} for customizing Gradle's toolchain support.
 *
 * @author Christoph Dreis
 */
public class ToolchainPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		configureToolchain(project);
	}

	private void configureToolchain(Project project) {
		// 这个使用新的方式,不再使用约定 ...
		// 为这个项目创建了一个扩展 ...
		// 使用的时候,就跟普通的方法一样使用 ...
		// Project 会自动进行代理 ...
		// https://www.yuque.com/gaolengdehulusi/nggog2/btuabt#pX6c7

		ToolchainExtension toolchain = project.getExtensions().create("toolchain", ToolchainExtension.class, project);

		// 好像是个空的 ...
		project.getLogger().info("解析出来的 toolchainVersion: {}",toolchain.getJavaVersion());
		// 解析出来的 test jvm 参数 ...
		project.getLogger().info("解析出来的 测试jvm 参数: {}", Arrays.toString(toolchain.getTestJvmArgs().get().toArray(new String[0])));
		JavaLanguageVersion toolchainVersion = toolchain.getJavaVersion();
		if(toolchainVersion != null && toolchainVersion.asInt() == 17) {
			System.out.println("测试 spring-boot项目中的 toolchain 扩展脚本块测试 .....");
			toolchainVersion = null;
		}
		if (toolchainVersion != null) {
			// 当项目评估之后, 做什么动作 ...
			project.afterEvaluate((evaluated) -> configure(evaluated, toolchain));
		}
	}

	private void configure(Project project, ToolchainExtension toolchain) {
		if (!isJavaVersionSupported(toolchain, toolchain.getJavaVersion())) {
			// 如果不支持,那么直接禁用掉工具链的任务 ...
			disableToolchainTasks(project);
		}
		else {
			// 拿到Java 插件扩展 中的工具链配置 ...
			JavaToolchainSpec toolchainSpec = project.getExtensions().getByType(JavaPluginExtension.class)
					.getToolchain();

			// 设置语言级别 ...
			toolchainSpec.getLanguageVersion().set(toolchain.getJavaVersion());
			// 配置测试工具链 ..
			configureTestToolchain(project, toolchain);
		}
	}

	private boolean isJavaVersionSupported(ToolchainExtension toolchain, JavaLanguageVersion toolchainVersion) {
		// 能够编译或者运行的条件肯定是  工具链版本必须要大于最大兼容Java 版本 ...
		return toolchain.getMaximumCompatibleJavaVersion().map((version) -> version.canCompileOrRun(toolchainVersion))
				.getOrElse(true);
	}

	private void disableToolchainTasks(Project project) {
		// 直接将 这些任务关闭 ...
		project.getTasks().withType(JavaCompile.class, (task) -> task.setEnabled(false));
		project.getTasks().withType(Javadoc.class, (task) -> task.setEnabled(false));
		project.getTasks().withType(Test.class, (task) -> task.setEnabled(false));
	}

	private void configureTestToolchain(Project project, ToolchainExtension toolchain) {
		List<String> jvmArgs = new ArrayList<>();
		jvmArgs.add("--illegal-access=warn");
		jvmArgs.addAll(toolchain.getTestJvmArgs().getOrElse(Collections.emptyList()));
		project.getTasks().withType(Test.class, (test) -> test.jvmArgs(jvmArgs));
	}

}
