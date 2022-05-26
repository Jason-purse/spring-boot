/*
 * Copyright 2012-2019 the original author or authors.
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
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LaunchedURLClassLoader}.
 *	自定义类加载器的测试 ...
 * @author Dave Syer
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
@SuppressWarnings("resource")
class LaunchedURLClassLoaderTests {

	@TempDir
	File tempDir;

	@Test
	void resolveResourceFromArchive() throws Exception {

		// 加载jar中的Entry ...
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResource("demo/Application.java")).isNotNull();
	}

	@Test
	void resolveResourcesFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResources("demo/Application.java").hasMoreElements()).isTrue();
	}

	@Test
	void resolveRootPathFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResource("")).isNotNull();

		// 获取一个的时候 直接拿取了系统本身具备的其中一个 ...
		// 根据注册的loader 顺序处理 ..(按道理来说,应该是 双亲委派机制加载)
		System.out.println(loader.getResource(""));
	}

	@Test
	void resolveRootResourcesFromArchive() throws Exception {
		LaunchedURLClassLoader loader = new LaunchedURLClassLoader(
				// 还可以使用相对路径 ...
				new URL[] { new URL("jar:file:src/test/resources/jars/app.jar!/") }, getClass().getClassLoader());
		assertThat(loader.getResources("").hasMoreElements()).isTrue();

		// 这里应该是多个 ...

		// 系统本身的  /  URLClassLoader 本身加载的  / 当前类加载器加载的 ... 当然没有 ...
		Enumeration<URL> resources = loader.getResources("");
		while (resources.hasMoreElements()) {
			System.out.println(resources.nextElement());
		}

		// 正因如此 我们根据包名进行加载 可能才有多个路径上的资源 ....
	}

	@Test
	void resolveFromNested() throws Exception {
		File file = new File(this.tempDir, "test.jar");
		TestJarCreator.createTestJar(file);

		// 根据Jar File 处理它...
		try (JarFile jarFile = new JarFile(file)) {
			// 获取URL ...
			URL url = jarFile.getUrl();
			System.out.println(String.format("父目录: %s", url));
			// 取消双亲委派 直接加载 ...
			try (LaunchedURLClassLoader loader = new LaunchedURLClassLoader(new URL[] { url }, null)) {
				URL resource = loader.getResource("nested.jar!/3.dat");
				if(resource != null) {
					System.out.println(String.format("resource url: %s", resource.toURI()));
				}
				assertThat(resource.toString()).isEqualTo(url + "nested.jar!/3.dat");
				try (InputStream input = resource.openConnection().getInputStream()) {
					assertThat(input.read()).isEqualTo(3);
				}
				URL resource1 = loader.getResource("./");
				if(resource1 != null) {
					File file1 = new File(loader.getResource("").toURI());
					JarFile jarEntries = new JarFile(file1);
					System.out.println(jarEntries.getEntry("nested.jar"));
				}
				else {
					System.out.println("全是空");
				}
			}
		}

		// 处理



	}

	@Test
	void resolveFromNestedWhileThreadIsInterrupted() throws Exception {
		File file = new File(this.tempDir, "test.jar");
		TestJarCreator.createTestJar(file);
		try (JarFile jarFile = new JarFile(file)) {
			URL url = jarFile.getUrl();
			try (LaunchedURLClassLoader loader = new LaunchedURLClassLoader(new URL[] { url }, null)) {
				Thread.currentThread().interrupt();
				URL resource = loader.getResource("nested.jar!/3.dat");
				assertThat(resource.toString()).isEqualTo(url + "nested.jar!/3.dat");
				URLConnection connection = resource.openConnection();
				try (InputStream input = connection.getInputStream()) {
					assertThat(input.read()).isEqualTo(3);
				}
				((JarURLConnection) connection).getJarFile().close();
			}
			finally {
				Thread.interrupted();
			}
		}
	}

}
