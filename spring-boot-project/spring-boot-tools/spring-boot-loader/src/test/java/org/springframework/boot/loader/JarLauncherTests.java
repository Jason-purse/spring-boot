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

package org.springframework.boot.loader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.testsupport.compiler.TestCompiler;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JarLauncher}.
 *
 * @author Andy Wilkinson
 * @author Madhura Bhave
 */
class JarLauncherTests extends AbstractExecutableArchiveLauncherTests {


	/**
	 * 游离的Jar(只有Boot-Inf / classes / 以及 lib
	 * @throws Exception
	 */
	@Test
	void explodedJarHasOnlyBootInfClassesAndContentsOfBootInfLibOnClasspath() throws Exception {
		// 解压 ...
		File explodedRoot = explode(createJarArchive("archive.jar", "BOOT-INF"));
		// 创建一个JarLauncher ...
		JarLauncher launcher = new JarLauncher(new ExplodedArchive(explodedRoot, true));
		List<Archive> archives = new ArrayList<>();
		// 获取类路径上的nest Archives...
		// 深度遍历优先算法 .. 如果没有一个entry 没有东西,那么它本身会打印出来,否则打印它的子内容 ...
		launcher.getClassPathArchivesIterator().forEachRemaining(archives::add);
		assertThat(getUrls(archives)).containsExactlyInAnyOrder(getExpectedFileUrls(explodedRoot));
		System.out.println("----------------- archives --------------------------");
		char i = 0;
		for (Archive archive : archives) {
			System.out.printf("archive %s%sn", archive.getUrl(),((byte) i++));
			archive.close();
		}
	}




	@Test
	void archivedJarHasOnlyBootInfClassesAndContentsOfBootInfLibOnClasspath() throws Exception {
		File jarRoot = createJarArchive("archive.jar", "BOOT-INF");
		try (JarFileArchive archive = new JarFileArchive(jarRoot)) {
			JarLauncher launcher = new JarLauncher(archive);
			List<Archive> classPathArchives = new ArrayList<>();
			launcher.getClassPathArchivesIterator().forEachRemaining(classPathArchives::add);
			assertThat(classPathArchives).hasSize(4);
			assertThat(getUrls(classPathArchives)).containsOnly(
					new URL("jar:" + jarRoot.toURI().toURL() + "!/BOOT-INF/classes!/"),
					new URL("jar:" + jarRoot.toURI().toURL() + "!/BOOT-INF/lib/foo.jar!/"),
					new URL("jar:" + jarRoot.toURI().toURL() + "!/BOOT-INF/lib/bar.jar!/"),
					new URL("jar:" + jarRoot.toURI().toURL() + "!/BOOT-INF/lib/baz.jar!/"));
			for (Archive classPathArchive : classPathArchives) {
				classPathArchive.close();
			}
		}
	}

	@Test
	void explodedJarShouldPreserveClasspathOrderWhenIndexPresent() throws Exception {
		File explodedRoot = explode(createJarArchive("archive.jar", "BOOT-INF", true, Collections.emptyList()));
		//File explodedRoot = explode(createJarArchive("archive.jar", "BOOT-INF", true, Arrays.asList("123.jar","456.jar")));
		JarLauncher launcher = new JarLauncher(new ExplodedArchive(explodedRoot, true));
		Iterator<Archive> archives = launcher.getClassPathArchivesIterator();
		URLClassLoader classLoader = (URLClassLoader) launcher.createClassLoader(archives);
		URL[] urls = classLoader.getURLs();
		System.out.println(Arrays.toString(urls));
		assertThat(urls).containsExactly(getExpectedFileUrls(explodedRoot));
	}

	@Test
	void jarFilesPresentInBootInfLibsAndNotInClasspathIndexShouldBeAddedAfterBootInfClasses() throws Exception {
		ArrayList<String> extraLibs = new ArrayList<>(Arrays.asList("extra-1.jar", "extra-2.jar"));
		// 将它追加到对应的目录中 ...
		File explodedRoot = explode(createJarArchive("archive.jar", "BOOT-INF", true, extraLibs));
		JarLauncher launcher = new JarLauncher(new ExplodedArchive(explodedRoot, true));
		Iterator<Archive> archives = launcher.getClassPathArchivesIterator();
		URLClassLoader classLoader = (URLClassLoader) launcher.createClassLoader(archives);
		URL[] urls = classLoader.getURLs();
		List<File> expectedFiles = getExpectedFilesWithExtraLibs(explodedRoot);
		URL[] expectedFileUrls = expectedFiles.stream().map(this::toUrl).toArray(URL[]::new);
		assertThat(urls).containsExactly(expectedFileUrls);
	}

	@Test
	void explodedJarDefinedPackagesIncludeManifestAttributes() throws Exception {

		// 设置清单属性 ...

		Manifest manifest = new Manifest();
		Attributes attributes = manifest.getMainAttributes();
		attributes.put(Name.MANIFEST_VERSION, "1.0");
		attributes.put(Name.IMPLEMENTATION_TITLE, "test");
		File explodedRoot = explode(
				createJarArchive("archive.jar", manifest, "BOOT-INF", true, Collections.emptyList()));
		// 测试编译器, 直接编译  制定了类文件输出位置  ....
		TestCompiler compiler = new TestCompiler(new File(explodedRoot, "BOOT-INF/classes"));
		// 指定了临时目录下面的 对应类
		File source = new File(this.tempDir, "explodedsample/ExampleClass.java");
		source.getParentFile().mkdirs();
		// 然后copy
		FileCopyUtils.copy(new File("src/test/resources/explodedsample/ExampleClass.txt"), source);
		// 进行编译 调用 ...
		compiler.getTask(Collections.singleton(source)).call();

		JarLauncher launcher = new JarLauncher(new ExplodedArchive(explodedRoot, true));
		Iterator<Archive> archives = launcher.getClassPathArchivesIterator();
		URLClassLoader classLoader = (URLClassLoader) launcher.createClassLoader(archives);
		Class<?> loaded = classLoader.loadClass("explodedsample.ExampleClass");  // 可以看出来 通过URLClassLoader 我们可以根据指定的URLPath路径下查找这些类 ....
		assertThat(loaded.getPackage().getImplementationTitle()).isEqualTo("test");
	}

	protected final URL[] getExpectedFileUrls(File explodedRoot) {
		return getExpectedFiles(explodedRoot).stream().map(this::toUrl).toArray(URL[]::new);
	}

	protected final List<File> getExpectedFiles(File parent) {
		List<File> expected = new ArrayList<>();
		expected.add(new File(parent, "BOOT-INF/classes"));
		expected.add(new File(parent, "BOOT-INF/lib/foo.jar"));
		expected.add(new File(parent, "BOOT-INF/lib/bar.jar"));
		expected.add(new File(parent, "BOOT-INF/lib/baz.jar"));
		return expected;
	}

	protected final List<File> getExpectedFilesWithExtraLibs(File parent) {
		List<File> expected = new ArrayList<>();
		expected.add(new File(parent, "BOOT-INF/classes"));
		expected.add(new File(parent, "BOOT-INF/lib/extra-1.jar"));
		expected.add(new File(parent, "BOOT-INF/lib/extra-2.jar"));
		expected.add(new File(parent, "BOOT-INF/lib/foo.jar"));
		expected.add(new File(parent, "BOOT-INF/lib/bar.jar"));
		expected.add(new File(parent, "BOOT-INF/lib/baz.jar"));
		return expected;
	}

}
