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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.spring.javaformat.gradle.SpringJavaFormatPlugin;
import io.spring.javaformat.gradle.tasks.CheckFormat;
import io.spring.javaformat.gradle.tasks.Format;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testretry.TestRetryPlugin;
import org.gradle.testretry.TestRetryTaskExtension;

import org.springframework.boot.build.optional.OptionalDependenciesPlugin;
import org.springframework.boot.build.testing.TestFailuresPlugin;
import org.springframework.boot.build.toolchain.ToolchainPlugin;

/**
 * 当此插件被应用 的时候-> 当JavaBasePlugin 出现的时候,以下约定将会应用
 * 	1. 此项目配置源和目标兼容性为1.8
 * 	2. SpringJavaFormatPlugin 以及 CheckstylePlugin,TestFailuresPlugin 以及 TestRetryPlugin 测试重置插件将会被应用 ...
 * 		// 于是我们在学习过程中,可以选择性的关闭一些插件 ... 否则例如doc注释都无法编译通过 ...
 * 	3. 	测试任务将会被配置 ..
 * 		1. 使用Junit 平台
 *      2. 设置最大堆内存 1024m;
 *      3. Checkstyle 以及 format 检测任务执行之后运行 ...
 *  4. 对 {@code org.junit.platform:junit-platform-launcher} 的 {@code testRuntimeOnly} 依赖被添加到应用了 {@link JavaPlugin} 的项目中
 *  5.  Java编译 /Javadoc / Format 任务配置使用UTF-8编码 ...
 *  6. Java编译任务配置 使用过-parameters.. 参数 ...
 *  		使用此参数编译具有一定的好处(例如注解处理器能够读取参数名称,在Java Plugin 处理过程中)
 *  		详情查看 https://www.yuque.com/gaolengdehulusi/nggog2/nbgs49#MY80t
 *  7.	当使用Java8构建的时候,Java编译任务同样配置为:
 *   		1. 处理警告作为错误 ..(例如我们使用sun的api 将会导致编译失败,所以我们去掉了-Werror 编译选项)
 *  		2. 启用了unchecked / deprecation / rawtypes / varags 警告 ..
 *  8. Jar 任务被配置去产生具有LICENSE.txt 以及 NOTICE.txt文件的jar 并且具有以下的manifest 条目 ...
 *   		1. Automatic-Module-Name 至于为什么建议拥有此条目,请查看 https://www.yuque.com/gaolengdehulusi/nggog2/oygdgd#a5Mbm
 *  		2. Build-Jdk-Spec 不是很清楚 ... 应该是实现厂商相关的设置吧 ..
 *   		3. 由谁构建
 *   		4. 实现标题
 *   		5. 实现版本
 *  9. spring-boot-parent 被用作依赖管理 ...
 * Conventions that are applied in the presence of the {@link JavaBasePlugin}. When the
 * plugin is applied:
 * <ul>
 * <li>The project is configured with source and target compatibility of 1.8
 * <li>{@link SpringJavaFormatPlugin Spring Java Format}, {@link CheckstylePlugin
 * Checkstyle}, {@link TestFailuresPlugin Test Failures}, and {@link TestRetryPlugin Test
 * Retry} plugins are applied
 *
 * <li>{@link Test} tasks are configured:
 * <ul>
 * <li>to use JUnit Platform
 * <li>with a max heap of 1024M
 * <li>to run after any Checkstyle and format checking tasks
 * </ul>
 * <li>A {@code testRuntimeOnly} dependency upon
 * {@code org.junit.platform:junit-platform-launcher} is added to projects with the
 * {@link JavaPlugin} applied
 * <li>{@link JavaCompile}, {@link Javadoc}, and {@link Format} tasks are configured to
 * use UTF-8 encoding
 * <li>{@link JavaCompile} tasks are configured to use {@code -parameters}.
 *
 * <li>When building with Java 8, {@link JavaCompile} tasks are also configured to:
 *
 * <ul>
 * <li>Treat warnings as errors
 * <li>Enable {@code unchecked}, {@code deprecation}, {@code rawtypes}, and {@code varags}
 * warnings
 * </ul>
 * <li>{@link Jar} tasks are configured to produce jars with LICENSE.txt and NOTICE.txt
 * files and the following manifest entries:
 *
 * <ul>
 * <li>{@code Automatic-Module-Name}
 * <li>{@code Build-Jdk-Spec}
 * <li>{@code Built-By}
 * <li>{@code Implementation-Title}
 * <li>{@code Implementation-Version}
 * </ul>
 * <li>{@code spring-boot-parent} is used for dependency management</li>
 * </ul>
 *
 * <p/>
 *
 * @author Andy Wilkinson
 * @author Christoph Dreis
 * @author Mike Smithson
 * @author Scott Frederick
 */
class JavaConventions {

//	详情查看 ...https://www.yuque.com/gaolengdehulusi/nggog2/nbgs49#e4QbF 选项1 和选项2
	private static final String SOURCE_AND_TARGET_COMPATIBILITY = "1.8";


	// 插件 方法 ..调用入口
	void apply(Project project) {
		// withType ...  立即触发插件的初始化
		// 对于具有JavaBasePlugin 应用的时候, 使用以下插件 ..
		project.getPlugins().withType(JavaBasePlugin.class, (java) -> {

			project.getPlugins().apply(TestFailuresPlugin.class);
			//configureSpringJavaFormat(project);
			configureJavaConventions(project);
			// 配置Javadoc 约定 ..
			configureJavadocConventions(project);
			// 配置测试 约定 ..
			configureTestConventions(project);
			configureJarManifestConventions(project);
			configureDependencyManagement(project);
			configureToolchain(project);
		});
	}

	private void configureJarManifestConventions(Project project) {
		ExtractResources extractLegalResources = project.getTasks().create("extractLegalResources",
				ExtractResources.class);
		extractLegalResources.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("legal"));
		extractLegalResources.setResourcesNames(Arrays.asList("LICENSE.txt", "NOTICE.txt"));
		extractLegalResources.property("version", project.getVersion().toString());
		SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
		Set<String> sourceJarTaskNames = sourceSets.stream().map(SourceSet::getSourcesJarTaskName)
				.collect(Collectors.toSet());
		Set<String> javadocJarTaskNames = sourceSets.stream().map(SourceSet::getJavadocJarTaskName)
				.collect(Collectors.toSet());
		project.getTasks().withType(Jar.class, (jar) -> project.afterEvaluate((evaluated) -> {
			jar.metaInf((metaInf) -> metaInf.from(extractLegalResources));
			jar.manifest((manifest) -> {
				Map<String, Object> attributes = new TreeMap<>();
				attributes.put("Automatic-Module-Name", project.getName().replace("-", "."));
				attributes.put("Build-Jdk-Spec", SOURCE_AND_TARGET_COMPATIBILITY);
				attributes.put("Built-By", "Spring");
				attributes.put("Implementation-Title",
						determineImplementationTitle(project, sourceJarTaskNames, javadocJarTaskNames, jar));
				attributes.put("Implementation-Version", project.getVersion());
				manifest.attributes(attributes);
			});
		}));
	}

	private String determineImplementationTitle(Project project, Set<String> sourceJarTaskNames,
			Set<String> javadocJarTaskNames, Jar jar) {
		if (sourceJarTaskNames.contains(jar.getName())) {
			return "Source for " + project.getName();
		}
		if (javadocJarTaskNames.contains(jar.getName())) {
			return "Javadoc for " + project.getName();
		}
		return project.getDescription();
	}

	private void configureTestConventions(Project project) {
		project.getTasks().withType(Test.class, (test) -> {
			// 使用JunitPlatform() 进行处理 ...
			test.useJUnitPlatform();
			test.setMaxHeapSize("1024M");
//			设置依赖关系 ..
			// 根据任务实例化避免规则,这种方式不在建议,并且应该使用新的API方式配置,但是如果更改之后,这种称为依赖配置陷阱 ..
			// 如果更改API之后,例如named / register ... 惰性任务构建 ... 但是不改变依赖配置方式(那就是配置陷阱,因为例如A 依赖于B ,但目前的配置是 A / B 同时实例化,所以没问题)
			// 但是 A / B 惰性初始化之后, A 依赖于 B ,那么应该A是消费者 . 反向配置在B中,那么将可能会失效(例如B没有实例化)
			// 所以  将依赖声明设置为正确的形式,在A中惰性配置闭包中配置 A depends on B ... 这样不管B自动配置与否都会正确的根据任务图执行) ...

			// https://docs.gradle.org/current/userguide/tutorial_using_tasks.html#sec:task_dependencies
			// 包括动态任务增加 ...
			project.getTasks().withType(Checkstyle.class, (checkstyle) -> test.mustRunAfter(checkstyle));
			project.getTasks().withType(CheckFormat.class, (checkFormat) -> test.mustRunAfter(checkFormat));
		});
		project.getPlugins().withType(JavaPlugin.class, (javaPlugin) -> project.getDependencies()
				.add(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.platform:junit-platform-launcher"));
		project.getPlugins().apply(TestRetryPlugin.class);
		project.getTasks().withType(Test.class,
				(test) -> project.getPlugins().withType(TestRetryPlugin.class, (testRetryPlugin) -> {
					TestRetryTaskExtension testRetry = test.getExtensions().getByType(TestRetryTaskExtension.class);
					testRetry.getFailOnPassedAfterRetry().set(true);
					testRetry.getMaxRetries().set(isCi() ? 3 : 0);
				}));
	}

	private boolean isCi() {
		return Boolean.parseBoolean(System.getenv("CI"));
	}

	private void configureJavadocConventions(Project project) {
		// 配置编码 ...
		project.getTasks().withType(Javadoc.class, (javadoc) -> {
			javadoc.getOptions().source("1.8").encoding("UTF-8");
		});
	}

	private void configureJavaConventions(Project project) {
		// 默认依照1.8 进行构建
		if (!project.hasProperty("toolchainVersion")) {
			// 如果没有工具链 ..
			// 工具链保证
			JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
			javaPluginExtension.setSourceCompatibility(JavaVersion.toVersion(SOURCE_AND_TARGET_COMPATIBILITY));
		}
		// 针对JavaCompile 任务 配置 编译器参数 ..
		// 开启 --parameters 编译,让编译器能够看到参数名称 ...
		project.getTasks().withType(JavaCompile.class, (compile) -> {
			compile.getOptions().setEncoding("UTF-8");
			List<String> args = compile.getOptions().getCompilerArgs();
			if (!args.contains("-parameters")) {
				args.add("-parameters");
			}
			// 如果包含了工具链版本,那么设置编译器的源兼容能力 以及目标兼容能力 ...
			if (project.hasProperty("toolchainVersion")) {
				// 一般来说,我们需要指定此编译任务的兼容能力 ..
				// 编译 源代码所指定的Java 语言级别兼容能力 ...
				compile.setSourceCompatibility(SOURCE_AND_TARGET_COMPATIBILITY);

				// 生成class文件的 JVM 语言级别
				compile.setTargetCompatibility(SOURCE_AND_TARGET_COMPATIBILITY);
			}
			// 如果使用Gradle 1.8 构建 ... 增加 -Werror (将警告作为错误) ...
			// 其他的一些参数类型 ... 可以查看
			else if (buildingWithJava8(project)) {
				// 取消 -Werror
				//"-Werror",
				// https://docs.oracle.com/en/java/javase/18/docs/specs/man/javac.html#standard-options
				args.addAll(Arrays.asList( "-Xlint:unchecked", "-Xlint:deprecation", "-Xlint:rawtypes",
						"-Xlint:varargs"));
			}
		});
	}

	private boolean buildingWithJava8(Project project) {
		return !project.hasProperty("toolchainVersion") && JavaVersion.current() == JavaVersion.VERSION_1_8;
	}

	private void configureSpringJavaFormat(Project project) {
		project.getPlugins().apply(SpringJavaFormatPlugin.class);
		project.getTasks().withType(Format.class, (Format) -> Format.setEncoding("UTF-8"));
		project.getPlugins().apply(CheckstylePlugin.class);
		CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
		checkstyle.setToolVersion("8.45.1");
		checkstyle.getConfigDirectory().set(project.getRootProject().file("src/checkstyle"));
		String version = SpringJavaFormatPlugin.class.getPackage().getImplementationVersion();
		DependencySet checkstyleDependencies = project.getConfigurations().getByName("checkstyle").getDependencies();
		// 不适用spring.javaformat
		//checkstyleDependencies
		//		.add(project.getDependencies().create("io.spring.javaformat:spring-javaformat-checkstyle:" + version));
	}

	private void configureDependencyManagement(Project project) {
		ConfigurationContainer configurations = project.getConfigurations();
		Configuration dependencyManagement = configurations.create("dependencyManagement", (configuration) -> {
			configuration.setVisible(false);
			configuration.setCanBeConsumed(false);
			configuration.setCanBeResolved(false);
		});
		configurations
				.matching((configuration) -> configuration.getName().endsWith("Classpath")
						|| JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME.equals(configuration.getName()))
				.all((configuration) -> configuration.extendsFrom(dependencyManagement));
		Dependency springBootParent = project.getDependencies().enforcedPlatform(project.getDependencies()
				.project(Collections.singletonMap("path", ":spring-boot-project:spring-boot-parent")));
		dependencyManagement.getDependencies().add(springBootParent);
		project.getPlugins().withType(OptionalDependenciesPlugin.class, (optionalDependencies) -> configurations
				.getByName(OptionalDependenciesPlugin.OPTIONAL_CONFIGURATION_NAME).extendsFrom(dependencyManagement));
	}

	private void configureToolchain(Project project) {
		project.getPlugins().apply(ToolchainPlugin.class);
	}

}
