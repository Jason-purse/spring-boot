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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.jar.Handler;
import sun.misc.Resource;

/**
 * {@link ClassLoader} used by the {@link Launcher}.
 *  Spring 自定义的  类加载器 ...
 *
 *  Spring 的自定义类加载器  用于在加载类的时候  重定义包信息 ...
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class LaunchedURLClassLoader extends URLClassLoader {

	private static final int BUFFER_SIZE = 4096;

	static {
		// 尽可能并行注册类加载器 .. (这里的caller 是ClassLoader)
		ClassLoader.registerAsParallelCapable();
	}

	private final boolean exploded;

	// 根部 归档文件
	private final Archive rootArchive;

	private final Object packageLock = new Object();

	// 定义包定义调用类型 ...
	private volatile DefinePackageCallType definePackageCallType;

	/**
	 * Create a new {@link LaunchedURLClassLoader} instance.
	 * @param urls the URLs from which to load classes and resources
	 * @param parent the parent class loader for delegation
	 */
	public LaunchedURLClassLoader(URL[] urls, ClassLoader parent) {
		this(false, urls, parent);
	}

	/**
	 * Create a new {@link LaunchedURLClassLoader} instance.
	 * @param exploded if the underlying archive is exploded   如果底层的归档 已经暴露,设置暴露标志 ..
	 * @param urls the URLs from which to load classes and resources  加载类以及资源的URL (统一资源定位符)
	 * @param parent the parent class loader for delegation
	 */
	public LaunchedURLClassLoader(boolean exploded, URL[] urls, ClassLoader parent) {
		this(exploded, null, urls, parent);
	}

	/**
	 * Create a new {@link LaunchedURLClassLoader} instance.
	 * @param exploded if the underlying archive is exploded
	 * @param rootArchive the root archive or {@code null}  最外层的 归档文件或者null ..
	 * @param urls the URLs from which to load classes and resources  加载classes 和 resources 的URL ...
	 * @param parent the parent class loader for delegation 父类加载器 ..(加载这个类的类加载器 应该就是AppClassLoader )
	 * @since 2.3.1
	 */
	public LaunchedURLClassLoader(boolean exploded, Archive rootArchive, URL[] urls, ClassLoader parent) {
		super(urls, parent);
		this.exploded = exploded;
		this.rootArchive = rootArchive;
	}

	// 决定了如何发现资源 ...
	@Override
	public URL findResource(String name) {
		// 如果是目录  直接常规解析方式即可 ...
		if (this.exploded) {
			return super.findResource(name);
		}
		// 但是jar中 需要使用协议处理 ...
		Handler.setUseFastConnectionExceptions(true);
		try {
			// 也是基于 URLClassPath 查找资源 ...
			return super.findResource(name);
		}
		finally {
			Handler.setUseFastConnectionExceptions(false);
		}
	}

	@Override
	public Enumeration<URL> findResources(String name) throws IOException {
		if (this.exploded) {
			return super.findResources(name);
		}
		Handler.setUseFastConnectionExceptions(true);
		try {
			return new UseFastConnectionExceptionsEnumeration(super.findResources(name));
		}
		finally {
			Handler.setUseFastConnectionExceptions(false);
		}
	}



	/**
	 * 加载一个类  定制 ..
	 *   继承 URLClassLoader 的原因可能是因为 {@link URLClassLoader#findClass(String)} 根据URL 进行resource 读取
	 *   并在 {@link  URLClassLoader#defineClass(String, Resource)} 中根据给定的package 进行class 的声明 ...
	 *   猜测是在这里做了手脚,让class 能够有立足之地  ...
	 *   所以 java -jar 运行 springboot 打包项目 (如此自然)
	 *
	 *   因为对应的包需要和Manifest 关联 ..(而这个 类加载器  重新定义了定义包 的方法）
	 * @param name class name
	 * @param resolve 是否直接初始化 。。
	 * @return Class<?>
	 * @throws ClassNotFoundException
	 */
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		// 当前 spring-boot-loader module 下的包名开始 ...
		if (name.startsWith("org.springframework.boot.loader.jarmode.")) {
			// 尝试在此类加载器中加载类 ..
			try {
				Class<?> result = loadClassInLaunchedClassLoader(name);
				// 是否直接解析这个类
				// 执行初始化代码块 ...
				if (resolve) {
					resolveClass(result);
				}
				// 否则直接返回 ...
				return result;

				// 疑问?
				// 这里为什么不直接调用 super.loadClass(name,resolve)
				// 因为这是内嵌jar的自定义加载方式 ...
			}
			catch (ClassNotFoundException ex) {
				// 尝试用普通形式加载(例如可能重名的其他内部jar的class) 或者 Class...
			}
		}

		// 如果是暴露,游离的 ...
		if (this.exploded) {
			// 尝试父类加载器加载类 ...
			// 仅仅外部jar才允许这种加载方式 .. 否则无法找到resource ...
			return super.loadClass(name, resolve);
		}

		// 那么既然它不是暴露的(也就不是外层jar的resource)
		// 设置快速连接失败异常 ...
		Handler.setUseFastConnectionExceptions(true);

		try {
			try {
				// 这里的异常捕获好像没有意义
				definePackageIfNecessary(name);
			}
			catch (IllegalArgumentException ex) {
				// Tolerate race condition due to being parallel capable
				if (getPackage(name) == null) {
					// This should never happen as the IllegalArgumentException indicates
					// that the package has already been defined and, therefore,
					// getPackage(name) should not return null.
					throw new AssertionError("Package " + name + " has already been defined but it could not be found");
				}
			}

			// 尝试用父类加载器 加载class
			return super.loadClass(name, resolve);
		}
		finally {
			Handler.setUseFastConnectionExceptions(false);
		}
	}

	// 在当前类加载器中加载class
	private Class<?> loadClassInLaunchedClassLoader(String name) throws ClassNotFoundException {
		String internalName = name.replace('.', '/') + ".class";
		// 尝试通过 父类加载器获取 资源 as stream
		// 这里是因为 这个模块在 spring boot maven plugin 打包时  作为底层jar 信息,所以 应该用 常规类加载器加载
		InputStream inputStream = getParent().getResourceAsStream(internalName);
		// 如果这里本身就不存在,那么必然需要爆出  类没有发现的异常 ....
		if (inputStream == null) {
			throw new ClassNotFoundException(name);
		}
		try {
			try {
				// 然后尝试 通过字节流的形式读取 ..
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				byte[] buffer = new byte[BUFFER_SIZE];
				int bytesRead = -1;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
				inputStream.close();
				byte[] bytes = outputStream.toByteArray();

				// 通过defineClass 将类加载出来  ...
				// 如果有必要  我们也可以重构defineClass 使用自己的逻辑进行类编译构建加载 ...
				Class<?> definedClass = defineClass(name, bytes, 0, bytes.length);

				// 如果有必要 定义包 ...
				// 保留包的一些信息 ...
				// 但是为什么需要定义包呢 ..
				// 它说是为了和对应的manifest 关联 ...
				definePackageIfNecessary(name);
				return definedClass;
			}
			finally {
				// 最后关闭流
				inputStream.close();
			}
		}
		catch (IOException ex) {
			throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
		}
	}

	/**
	 * Define a package before a {@code findClass} call is made. This is necessary to
	 * ensure that the appropriate manifest for nested JARs is associated with the
	 * package.
	 *
	 * 在findClass 调用开始之前定义一个包 ..
	 * 这是必要的去确保 内嵌jar相关的manifest与此包关联 ...
	 * @param className the class name being found
	 */
	private void definePackageIfNecessary(String className) {
		int lastDot = className.lastIndexOf('.');
		if (lastDot >= 0) {
			// 截取包名 ..
			String packageName = className.substring(0, lastDot);

			// 通过祖先和当前类加载器发现包名 ..
			// 如果没有 ...
			if (getPackage(packageName) == null) {
				try {
					// 定义包名
					definePackage(className, packageName);
				}
				catch (IllegalArgumentException ex) {
					// Tolerate race condition due to being parallel capable
					// 容忍 竞争条件  由于开始可能的并发 ...
					if (getPackage(packageName) == null) {
						// This should never happen as the IllegalArgumentException
						// indicates that the package has already been defined and,
						// therefore, getPackage(name) should not have returned null.
						// 它说绝不可能发生 无效参数  这指示了包已经被定义 ..
						// 因此 getPackage 应该不可能返回null ...

						// 但是这是一个防御性编程 ...
						throw new AssertionError(
								"Package " + packageName + " has already been defined but it could not be found");
					}
				}
			}
		}
	}

	// 定义合适的包名 确保内嵌的jar 和对应的manifest 对应 ..
	private void definePackage(String className, String packageName) {
		try {
			// 权限访问控制 ... 判断堆栈是否有权限 ...
			AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
				String packageEntryName = packageName.replace('.', '/') + "/";
				String classEntryName = className.replace('.', '/') + ".class";
				// 获取加载类和资源的Urls ..
				for (URL url : getURLs()) {
					try {
						// 尝试 打开连接
						URLConnection connection = url.openConnection();
						// 如果是一个JarURLConnection ...
						// 详情查看这个类的doc 文档 ...
						if (connection instanceof JarURLConnection) {
							// 它获取JarFile ..
							JarFile jarFile = ((JarURLConnection) connection).getJarFile();
							// 拿到这个jarFile 就是为了获取一个Entry .. 并且如果不为空, 获取包也不为空 且 manifest 不为空 ...
							if (jarFile.getEntry(classEntryName) != null && jarFile.getEntry(packageEntryName) != null
									&& jarFile.getManifest() != null) {
								// 然后我们重新定义包名 ...
								definePackage(packageName, jarFile.getManifest(), url);
								return null;
							}
						}
					}
					catch (IOException ex) {
						// Ignore
					}
				}
				return null;
			}, AccessController.getContext());
		}
		catch (java.security.PrivilegedActionException ex) {
			// Ignore
		}
	}

	/**
	 * 定义包 ...
	 * @param name 包名
	 * @param man 清单
	 * @param url 查找类或者资源的url
	 * @return 一个包 ..
	 * @throws IllegalArgumentException
	 */
	@Override
	protected Package definePackage(String name, Manifest man, URL url) throws IllegalArgumentException {

		// 所以需要 搞清楚 explode 到底干了什么
		// 相当于 游离的包(也就是目录文件) ...
		if (!this.exploded) {
			// 包名 这里的manifest 用来读取版本和密闭信息
			// 对于一个游离的包,这些url 制定了code source(代码来源),此类或者资源从哪里加载 ...
			return super.definePackage(name, man, url);
		}
		// 否则外暴露的包 需要制定manifest ...
		// 这里的加载类 并行的 ..(猜测)
		// 所以加锁 ...
		synchronized (this.packageLock) { // 这里加载类并不是为了并行的,是为了维持调用链, 否则进入查询manifest  定义包的循环...
			return doDefinePackage(DefinePackageCallType.MANIFEST, () -> super.definePackage(name, man, url));
		}
	}

	@Override
	protected Package definePackage(String name, String specTitle, String specVersion, String specVendor,
			String implTitle, String implVersion, String implVendor, URL sealBase) throws IllegalArgumentException {
		if (!this.exploded) { // 非游离的包 java 定义包信息 ...
			return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor,
					sealBase);
		}

		// 否则 ??
		synchronized (this.packageLock) {

			// 如果定义包的调用类型为空 ..
			if (this.definePackageCallType == null) {

				// We're not part of a call chain which means that the URLClassLoader
				// is trying to define a package for our exploded JAR. We use the
				// manifest version to ensure package attributes are set

				// 我们不是调用链的一部分 - 这意味着URLClassLoader 尝试为我们暴露的JAR 定义一个包 ..
				// 我们使用这个manifest 版本确保包的属性被设置
				Manifest manifest = getManifest(this.rootArchive);
				// 如果清单不为空 ..., 这里为什么定义清单信息呢,因为父类已经找不到这些类了,并且它还没有清单信息,我们给它指定清单信息,然后尝试从中查询  应用代码,这是最后的机会,双亲委派已经任务完成 ...
				if (manifest != null) {
					// 从这里开始进入调用链...
					// 这里清单不为空,重新定义一次(处于让它开始调用链)
					return definePackage(name, manifest, sealBase);
				}
			}

			// 否则 在调用链中 ... 这一步处于定义包的一些属性 ...
			// 这里就真正开始属性设置 ...
			return doDefinePackage(DefinePackageCallType.ATTRIBUTES, () -> super.definePackage(name, specTitle,
					specVersion, specVendor, implTitle, implVersion, implVendor, sealBase));
		}
	}

	private Manifest getManifest(Archive archive) {
		try {
			return (archive != null) ? archive.getManifest() : null;
		}
		catch (IOException ex) {
			return null;
		}
	}


	// 根据 定义包调用类型  定义一个包 ...
	private <T> T doDefinePackage(DefinePackageCallType type, Supplier<T> call) {
		DefinePackageCallType existingType = this.definePackageCallType;
		// 保留上下文(当前流程结束之后,恢复它的之前的资源信息)
		// 这里的整个过程都有锁加持
		try {
			this.definePackageCallType = type;
			return call.get();
		}
		finally {
			this.definePackageCallType = existingType;
		}
	}

	/**
	 * Clear URL caches.
	 */
	public void clearCache() {
		if (this.exploded) {
			return;
		}
		for (URL url : getURLs()) {
			try {
				URLConnection connection = url.openConnection();
				if (connection instanceof JarURLConnection) {
					clearCache(connection);
				}
			}
			catch (IOException ex) {
				// Ignore
			}
		}

	}

	private void clearCache(URLConnection connection) throws IOException {
		Object jarFile = ((JarURLConnection) connection).getJarFile();
		if (jarFile instanceof org.springframework.boot.loader.jar.JarFile) {
			((org.springframework.boot.loader.jar.JarFile) jarFile).clearCache();
		}
	}

	private static class UseFastConnectionExceptionsEnumeration implements Enumeration<URL> {

		private final Enumeration<URL> delegate;

		UseFastConnectionExceptionsEnumeration(Enumeration<URL> delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean hasMoreElements() {
			Handler.setUseFastConnectionExceptions(true);
			try {
				return this.delegate.hasMoreElements();
			}
			finally {
				Handler.setUseFastConnectionExceptions(false);
			}

		}

		@Override
		public URL nextElement() {
			Handler.setUseFastConnectionExceptions(true);
			try {
				return this.delegate.nextElement();
			}
			finally {
				Handler.setUseFastConnectionExceptions(false);
			}
		}

	}

	/**
	 * The different types of call made to define a package. We track these for exploded
	 * jars so that we can detect packages that should have manifest attributes applied.
	 *
	 * 定义一个包的不同的调用模式,在暴露的jar中跟踪这些东西 -   检测这些包(应该具有mainfest 属性)
	 *
	 */
	private enum DefinePackageCallType {

		/**
		 * A define package call from a resource that has a manifest.
		 * 具有manifest 的包定义调用
		 */
		MANIFEST,

		/**
		 * A define package call with a direct set of attributes.
		 * 直接属性设置的包定义调用
		 */
		ATTRIBUTES

	}

}
