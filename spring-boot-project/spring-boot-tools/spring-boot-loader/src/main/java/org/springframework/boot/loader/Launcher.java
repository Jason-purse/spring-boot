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

package org.springframework.boot.loader;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;

/**
 * Base class for launchers that can start an application with a fully configured
 * classpath backed by one or more {@link Archive}s.
 *
 * 启动器的基类(它能够启动一个应用 - (这个应用完全由一个或者多个归档文件支持的类路径应用)
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @since 1.0.0
 */
public abstract class Launcher {

	private static final String JAR_MODE_LAUNCHER = "org.springframework.boot.loader.jarmode.JarModeLauncher";

	/**
	 *
	 * 启动应用的核心方法 这个方法是一个入口 - 它应该由子类的main 方法调用 ..
	 * Launch the application. This method is the initial entry point that should be
	 * called by a subclass {@code public static void main(String[] args)} method.
	 * @param args the incoming arguments
	 * @throws Exception if the application fails to launch
	 */
	protected void launch(String[] args) throws Exception {
		if (!isExploded()) {
			// 如果处于非分解模式,注册URL 协议处理器 ...
			JarFile.registerUrlProtocolHandler();
		}

		// 默认创建的是Launched...loader
		ClassLoader classLoader = createClassLoader(getClassPathArchivesIterator());


		String jarMode = System.getProperty("jarmode");
		String launchClass = (jarMode != null && !jarMode.isEmpty()) ? JAR_MODE_LAUNCHER : getMainClass();

		// 根据jar模式判断,启用哪一种 ..
		launch(args, launchClass, classLoader);
	}

	/**
	 * Create a classloader for the specified archives.
	 * @param archives the archives
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of
	 * {@link #createClassLoader(Iterator)}
	 */
	@Deprecated
	protected ClassLoader createClassLoader(List<Archive> archives) throws Exception {
		return createClassLoader(archives.iterator());
	}

	/**
	 * Create a classloader for the specified archives.
	 * @param archives the archives
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 * @since 2.3.0
	 */
	protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
		List<URL> urls = new ArrayList<>(50);
		while (archives.hasNext()) {
			urls.add(archives.next().getUrl());
		}
		return createClassLoader(urls.toArray(new URL[0]));
	}

	/**
	 * Create a classloader for the specified URLs.
	 * 根据给定的Url 创建类路径加载 ....器
	 * @param urls the URLs
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 */
	protected ClassLoader createClassLoader(URL[] urls) throws Exception {
		return new LaunchedURLClassLoader(isExploded(), getArchive(), urls, getClass().getClassLoader());
	}

	/**
	 * Launch the application given the archive file and a fully configured classloader.
	 *
	 * 启动一个应用 (通过给定的archive 和 完全配置的类加载器)
	 * @param args the incoming arguments
	 * @param launchClass the launch class to run
	 * @param classLoader the classloader
	 * @throws Exception if the launch fails
	 */
	protected void launch(String[] args, String launchClass, ClassLoader classLoader) throws Exception {
		// 设置了当前线程上下文的类加载器 ...
		// 那么所有由当前线程派生的线程都应该使用此加载器 ...
		Thread.currentThread().setContextClassLoader(classLoader);

		// 然后创建 启动器Runner run
		createMainMethodRunner(launchClass, args, classLoader).run();
	}

	/**
	 * Create the {@code MainMethodRunner} used to launch the application.
	 * @param mainClass the main class
	 * @param args the incoming arguments
	 * @param classLoader the classloader
	 * @return the main method runner
	 */
	protected MainMethodRunner createMainMethodRunner(String mainClass, String[] args, ClassLoader classLoader) {
		// 默认是使用MainMethodRunner ...
		return new MainMethodRunner(mainClass, args);
	}

	/**
	 * Returns the main class that should be launched.
	 * @return the name of the main class
	 * @throws Exception if the main class cannot be obtained
	 */
	protected abstract String getMainClass() throws Exception;

	/**
	 * Returns the archives that will be used to construct the class path.
	 * 返回归档文件(将被用来构建类路径)
	 * @return the class path archives
	 * @throws Exception if the class path archives cannot be obtained
	 * @since 2.3.0
	 */
	protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
		return getClassPathArchives().iterator();
	}

	/**
	 * Returns the archives that will be used to construct the class path.
	 * @return the class path archives
	 * @throws Exception if the class path archives cannot be obtained
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of implementing
	 * {@link #getClassPathArchivesIterator()}.
	 */
	@Deprecated
	protected List<Archive> getClassPathArchives() throws Exception {
		throw new IllegalStateException("Unexpected call to getClassPathArchives()");
	}

	// 创建一个Archive ...

	/**
	 * 所以从这个方法上看
	 * spring boot 必然会对spring boot maven plugin  打包时对Archive的保护域做手脚 ...
	 * @return
	 * @throws Exception
	 */
	protected final Archive createArchive() throws Exception {
		// 或者这个类的 保护域
		ProtectionDomain protectionDomain = getClass().getProtectionDomain();
		// 获取保护域中的代码源(也可以说叫做代码基)
		CodeSource codeSource = protectionDomain.getCodeSource();

		// 通过它获取Location ...
		URI location = (codeSource != null) ? codeSource.getLocation().toURI() : null;
		// 获取 Scheme规范部分内容 ...
		String path = (location != null) ? location.getSchemeSpecificPart() : null;
		if (path == null) {
			throw new IllegalStateException("Unable to determine code source archive");
		}
		// 然后根据这个路径 获取文件 ...
		// 既然如此  它应该是一个
		File root = new File(path);
		// 如果文件不存在,那么   抛出异常 ...
		if (!root.exists()) {
			throw new IllegalStateException("Unable to determine code source archive from " + root);
		}

		// 如果是一个目录,那么一个分离的Archive(就相当于没有压缩), 否则JarFile...
		return (root.isDirectory() ? new ExplodedArchive(root) : new JarFileArchive(root));
	}

	/**
	 * Returns if the launcher is running in an exploded mode. If this method returns
	 * {@code true} then only regular JARs are supported and the additional URL and
	 * ClassLoader support infrastructure can be optimized.
	 *
	 * 如果为true, 表示运行在一个分解模式 ...(有些explode 我翻译成了 暴露,error)
	 * 如果为true,那么仅仅支持普通的Jar 以及额外的URL 和 类加载器支持基础设施能够被优化 。。
	 *
	 * @return if the jar is exploded.
	 * @since 2.3.0
	 */
	protected boolean isExploded() {
		return false;
	}

	/**
	 * Return the root archive.
	 * @return the root archive
	 * @since 2.3.1
	 */
	protected Archive getArchive() {
		return null;
	}

}
