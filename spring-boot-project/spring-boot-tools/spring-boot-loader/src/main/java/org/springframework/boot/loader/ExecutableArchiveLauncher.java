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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Manifest;

import org.springframework.boot.loader.archive.Archive;

/**
 * Base class for executable archive {@link Launcher}s.
 * 可执行archive 的Launcher 的基类 ...
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @since 1.0.0
 */
public abstract class ExecutableArchiveLauncher extends Launcher {

	/**
	 * manifest 中的一个 属性 ... 表示main 类 ..
	 */
	private static final String START_CLASS_ATTRIBUTE = "Start-Class";

	protected static final String BOOT_CLASSPATH_INDEX_ATTRIBUTE = "Spring-Boot-Classpath-Index";

	private final Archive archive;

	private final ClassPathIndexFile classPathIndex;

	public ExecutableArchiveLauncher() {
		try {
			// 创建归档 ..
			this.archive = createArchive();
			// 创建ClassPath Index 文件..
			this.classPathIndex = getClassPathIndex(this.archive);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	protected ExecutableArchiveLauncher(Archive archive) {
		try {
			this.archive = archive;
			this.classPathIndex = getClassPathIndex(this.archive);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
	// 默认为空 ..
	protected ClassPathIndexFile getClassPathIndex(Archive archive) throws IOException {
		return null;
	}

	@Override
	protected String getMainClass() throws Exception {
		Manifest manifest = this.archive.getManifest();
		String mainClass = null;
		if (manifest != null) {
			// 获取清单中的入口类(本质上 spring boot maven plugin 插件将 入口类设置为了start_class 属性)  ..
			mainClass = manifest.getMainAttributes().getValue(START_CLASS_ATTRIBUTE);
		}
		// 否则直接报错 ..
		if (mainClass == null) {
			throw new IllegalStateException("No 'Start-Class' manifest entry specified in " + this);
		}
		return mainClass;
	}

	@Override
	protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
		List<URL> urls = new ArrayList<>(guessClassPathSize());
		while (archives.hasNext()) {
			urls.add(archives.next().getUrl());
		}
		if (this.classPathIndex != null) {
			urls.addAll(this.classPathIndex.getUrls());
		}
		return createClassLoader(urls.toArray(new URL[0]));
	}

	private int guessClassPathSize() {
		if (this.classPathIndex != null) {
			return this.classPathIndex.size() + 10;
		}
		return 50;
	}

	@Override
	protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
		// 查询候选过滤器 ...
		// 查看JarLauncher  它指定了/BOOT-INF/目录下面的归档进行处理 ...
		Archive.EntryFilter searchFilter = this::isSearchCandidate;
		// 返回内嵌的所有Archive 用来构建类路径 ...
		Iterator<Archive> archives = this.archive.getNestedArchives(searchFilter,
				// 同样JarLauncher 制定了 BOOT-INF/lib下的归档文件 增加到类路径上 ...
				(entry) -> isNestedArchive(entry) && !isEntryIndexed(entry));
		// 是否后置处理 类路径Archives ...
		if (isPostProcessingClassPathArchives()) {
			archives = applyClassPathArchivePostProcessing(archives);
		}
		return archives;
	}


	// 是否可被索引
	private boolean isEntryIndexed(Archive.Entry entry) {
		if (this.classPathIndex != null) {
			return this.classPathIndex.containsEntry(entry.getName());
		}
		return false;
	}

	private Iterator<Archive> applyClassPathArchivePostProcessing(Iterator<Archive> archives) throws Exception {
		List<Archive> list = new ArrayList<>();
		while (archives.hasNext()) {
			list.add(archives.next());
		}
		postProcessClassPathArchives(list);
		return list.iterator();
	}

	/**
	 * Determine if the specified entry is a candidate for further searching.
	 * @param entry the entry to check
	 * @return {@code true} if the entry is a candidate for further searching
	 * @since 2.3.0
	 */
	protected boolean isSearchCandidate(Archive.Entry entry) {
		return true;
	}

	/**
	 * Determine if the specified entry is a nested item that should be added to the
	 * classpath.
	 *
	 * 决定特定的entry 是否是一个内嵌的Item(是否应该增加到类路径上)
	 * @param entry the entry to check
	 * @return {@code true} if the entry is a nested item (jar or directory)
	 */
	protected abstract boolean isNestedArchive(Archive.Entry entry);

	/**
	 * Return if post processing needs to be applied to the archives. For back
	 * compatibility this method returns {@code true}, but subclasses that don't override
	 * {@link #postProcessClassPathArchives(List)} should provide an implementation that
	 * returns {@code false}.
	 *
	 *  向后兼容 返回true 如果子类没有覆盖postProce.... 应该提供一个返回false 的实现 ..
	 * @return if the {@link #postProcessClassPathArchives(List)} method is implemented
	 * @since 2.3.0
	 */
	protected boolean isPostProcessingClassPathArchives() {
		return true;
	}

	/**
	 * Called to post-process archive entries before they are used. Implementations can
	 * add and remove entries.
	 * @param archives the archives
	 * @throws Exception if the post processing fails
	 * @see #isPostProcessingClassPathArchives()
	 */
	protected void postProcessClassPathArchives(List<Archive> archives) throws Exception {
	}

	@Override
	protected boolean isExploded() {
		return this.archive.isExploded();
	}

	@Override
	protected final Archive getArchive() {
		return this.archive;
	}

}
