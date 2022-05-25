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
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.Archive.EntryFilter;
import org.springframework.boot.loader.archive.ExplodedArchive;

/**
 * {@link Launcher} for JAR based archives. This launcher assumes that dependency jars are
 * included inside a {@code /BOOT-INF/lib} directory and that application classes are
 * included inside a {@code /BOOT-INF/classes} directory.
 *
 * 一般来说 这是针对基于jar 的归档 启动器
 * 这个启动器 假设依赖的jar 包括在 /BOOT-INF/lib 目录中,并且应用类包括在 /BOOT-INF/classes目录中 ...
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @since 1.0.0
 */
public class JarLauncher extends ExecutableArchiveLauncher {

	// 获取类路径索引定位地址 .. 应该是它纪录了类路径上依赖的信息 ..
	private static final String DEFAULT_CLASSPATH_INDEX_LOCATION = "BOOT-INF/classpath.idx";

	// 实体过滤器
	static final EntryFilter NESTED_ARCHIVE_ENTRY_FILTER = (entry) -> {
		// 如果是目录 ... 判断是否在 classes中
		if (entry.isDirectory()) {
			return entry.getName().equals("BOOT-INF/classes/");
		}
		// 否则是否以/BOOT-INF/lib/ 开始
		return entry.getName().startsWith("BOOT-INF/lib/");
	};

	public JarLauncher() {
	}

	protected JarLauncher(Archive archive) {
		super(archive);
	}

	@Override
	protected ClassPathIndexFile getClassPathIndex(Archive archive) throws IOException {

		// 那么从这里可以知道 ,Spring boot 打包的Jar 称为需要分解archive的Jar..
		// 仅分解存档需要，常规存档已具有定义的顺序
		// Only needed for exploded archives, regular ones already have a defined order
		if (archive instanceof ExplodedArchive) {
			// 如果是这样  尝试获取 ...
			String location = getClassPathIndexFileLocation(archive);
			return ClassPathIndexFile.loadIfPossible(archive.getUrl(), location);
		}
		// 否则直接获取 但是默认为空 ...
		return super.getClassPathIndex(archive);
	}

	private String getClassPathIndexFileLocation(Archive archive) throws IOException {
		// 根据这个归档文件 获取清单信息
		Manifest manifest = archive.getManifest();
		// 如果清单存在
		Attributes attributes = (manifest != null) ? manifest.getMainAttributes() : null;
		// 获取索引文件地址
		String location = (attributes != null) ? attributes.getValue(BOOT_CLASSPATH_INDEX_ATTRIBUTE) : null;
		// 如果没有, 返回默认地址
		return (location != null) ? location : DEFAULT_CLASSPATH_INDEX_LOCATION;
	}

	@Override
	protected boolean isPostProcessingClassPathArchives() {
		return false;
	}

	@Override
	protected boolean isSearchCandidate(Archive.Entry entry) {
		return entry.getName().startsWith("BOOT-INF/");
	}

	@Override
	protected boolean isNestedArchive(Archive.Entry entry) {
		return NESTED_ARCHIVE_ENTRY_FILTER.matches(entry);
	}

	/**
	 * 入口类 ...
	 * @param args 参数
	 * @throws Exception 异常
	 */
	public static void main(String[] args) throws Exception {
		// 非常简单的new 一个对象 启动 ..
		new JarLauncher().launch(args);
	}

}
