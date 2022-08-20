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

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/**
 * 工具链插件的DSL
 *
 * DSL extension for {@link ToolchainPlugin}.
 *
 * @author Christoph Dreis
 */
public class ToolchainExtension {

	// 最大兼容版本 ...
	// 插件只支持三种类型的属性 ...

	// 当然这里的扩展 类型应该是随意的吧  后续了解 ...
	private final Property<JavaLanguageVersion> maximumCompatibleJavaVersion;

	private final ListProperty<String> testJvmArgs;

	private final JavaLanguageVersion javaVersion;

	// 这里应该是配置默认值
	public ToolchainExtension(Project project) {
		// 构建这样的一个扩展 ...
		// 尝试拿取一个JavaLanguageVersion 类型的属性 ...
		// 切记没有拿取当前gradle 构建所使用的 version , 因为有可能不一样(项目的想要使用的version 可能不一致) ....
		this.maximumCompatibleJavaVersion = project.getObjects().property(JavaLanguageVersion.class);
		//
		this.testJvmArgs = project.getObjects().listProperty(String.class);

		// 获取到了工具链版本 ...
		String toolchainVersion = (String) project.findProperty("toolchainVersion");
		// 进行解析 ...
		this.javaVersion = (toolchainVersion != null) ? JavaLanguageVersion.of(toolchainVersion) : null;

	}

	public Property<JavaLanguageVersion> getMaximumCompatibleJavaVersion() {
		return this.maximumCompatibleJavaVersion;
	}

	public ListProperty<String> getTestJvmArgs() {
		return this.testJvmArgs;
	}

	JavaLanguageVersion getJavaVersion() {
		return this.javaVersion;
	}

}
