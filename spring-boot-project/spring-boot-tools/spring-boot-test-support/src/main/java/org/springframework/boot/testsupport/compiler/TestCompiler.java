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

package org.springframework.boot.testsupport.compiler;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Wrapper to make the {@link JavaCompiler} easier to use in tests.
 *
 * 包装了Java 编译器更好的用于测试 。。
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 1.5.0
 */
public class TestCompiler {

	/**
	 * The default source directory.
	 */
	public static final File SOURCE_DIRECTORY = new File("src/test/java");

	private final JavaCompiler compiler;

	private final StandardJavaFileManager fileManager;

	private final File outputLocation;

	public TestCompiler(File outputLocation) throws IOException {
		this(ToolProvider.getSystemJavaCompiler(), outputLocation);
	}

	public TestCompiler(JavaCompiler compiler, File outputLocation) throws IOException {
		this.compiler = compiler;
		this.fileManager = compiler.getStandardFileManager(null, null, null);
		this.outputLocation = outputLocation;
		this.outputLocation.mkdirs();
		Iterable<? extends File> temp = Collections.singletonList(this.outputLocation);
		// 设置编译之后的类文件输出
		this.fileManager.setLocation(StandardLocation.CLASS_OUTPUT, temp);
		// 设置编译之后的资源输出位置 ..
		this.fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, temp);
	}

	public TestCompilationTask getTask(Collection<File> sourceFiles) {
		// 获取java 文件对象
		Iterable<? extends JavaFileObject> javaFileObjects = this.fileManager.getJavaFileObjectsFromFiles(sourceFiles);
		// 创建任务 ...
		return getTask(javaFileObjects);
	}

	public TestCompilationTask getTask(Class<?>... types) {
		Iterable<? extends JavaFileObject> javaFileObjects = getJavaFileObjects(types);
		return getTask(javaFileObjects);
	}

	private TestCompilationTask getTask(Iterable<? extends JavaFileObject> javaFileObjects) {
		return new TestCompilationTask(
				// 通过编译器创建任务 ...
				// 这里的classes 被用来进行 注解处理的类 ...
				this.compiler.getTask(null, this.fileManager, null, null, null, javaFileObjects)
		);
	}

	public File getOutputLocation() {
		return this.outputLocation;
	}

	private Iterable<? extends JavaFileObject> getJavaFileObjects(Class<?>... types) {
		File[] files = new File[types.length];
		for (int i = 0; i < types.length; i++) {
			files[i] = getFile(types[i]);
		}
		return this.fileManager.getJavaFileObjects(files);
	}

	protected File getFile(Class<?> type) {
		return new File(getSourceDirectory(), sourcePathFor(type));
	}

	public static String sourcePathFor(Class<?> type) {
		return type.getName().replace('.', '/') + ".java";
	}

	protected File getSourceDirectory() {
		return SOURCE_DIRECTORY;
	}

	/**
	 * A compilation task.
	 */
	public static class TestCompilationTask {

		/**
		 * 编译任务 ...
		 */
		private final CompilationTask task;

		public TestCompilationTask(CompilationTask task) {
			this.task = task;
		}

		// 注解处理器接口 ...
		public void call(Processor... processors) {
			// 可以设置注解处理器
			this.task.setProcessors(Arrays.asList(processors));
			// 编译失败,抛出异常 ..
			if (!this.task.call()) {
				throw new IllegalStateException("Compilation failed");
			}
		}

	}

}
