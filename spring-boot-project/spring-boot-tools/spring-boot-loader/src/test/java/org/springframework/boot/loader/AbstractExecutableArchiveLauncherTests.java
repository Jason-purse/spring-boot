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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.util.FileCopyUtils;

/**
 * Base class for testing {@link ExecutableArchiveLauncher} implementations.
 *
 * @author Andy Wilkinson
 * @author Madhura Bhave
 */
public abstract class AbstractExecutableArchiveLauncherTests {

	@TempDir
	File tempDir;

	protected File createJarArchive(String name, String entryPrefix) throws IOException {
		return createJarArchive(name, entryPrefix, false, Collections.emptyList());
	}

	@SuppressWarnings("resource")
	protected File createJarArchive(String name, String entryPrefix, boolean indexed, List<String> extraLibs)
			throws IOException {
		return createJarArchive(name, null, entryPrefix, indexed, extraLibs);
	}

	@SuppressWarnings("resource")
	protected File createJarArchive(String name, Manifest manifest, String entryPrefix, boolean indexed,
			List<String> extraLibs) throws IOException {
		File archive = new File(this.tempDir, name);

		// 通过jar 输出流  输出信息 ...
		JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(archive));
		if (manifest != null) {
			jarOutputStream.putNextEntry(new JarEntry("META-INF/"));
			jarOutputStream.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
			// 将清单写入 流中 ...
			manifest.write(jarOutputStream);
			jarOutputStream.closeEntry();
		}
		jarOutputStream.putNextEntry(new JarEntry(entryPrefix + "/"));
		jarOutputStream.putNextEntry(new JarEntry(entryPrefix + "/classes/"));
		jarOutputStream.putNextEntry(new JarEntry(entryPrefix + "/lib/"));

		// 是否应该索引,如果是 ... 加入这几个 索引条目 ...
		if (indexed) {
			// 默认应该是会打开这个Entry 进行处理的 ..
			jarOutputStream.putNextEntry(new JarEntry(entryPrefix + "/classpath.idx"));
			// 输出流写入器 ..
			Writer writer = new OutputStreamWriter(jarOutputStream, StandardCharsets.UTF_8);
			writer.write("- \"BOOT-INF/lib/foo.jar\"\n");
			writer.write("- \"BOOT-INF/lib/bar.jar\"\n");
			writer.write("- \"BOOT-INF/lib/baz.jar\"\n");
			writer.flush();
			jarOutputStream.closeEntry();
		}
		// 给 foo.jar 增加内嵌Jar ...
		addNestedJars(entryPrefix, "/lib/foo.jar", jarOutputStream);
		addNestedJars(entryPrefix, "/lib/bar.jar", jarOutputStream);
		addNestedJars(entryPrefix, "/lib/baz.jar", jarOutputStream);

		// 增加内嵌的Jar
		for (String lib : extraLibs) {
			// f-each ... 增加到lib中 ...
			addNestedJars(entryPrefix, "/lib/" + lib, jarOutputStream);
		}
		jarOutputStream.close();
		return archive;
	}

	private void addNestedJars(String entryPrefix, String lib, JarOutputStream jarOutputStream) throws IOException {
		JarEntry libFoo = new JarEntry(entryPrefix + lib);
		// 内部这个jar 不能被压缩(只能被存储) 所以方式为0
		libFoo.setMethod(ZipEntry.STORED);
		// 字节数组输出流
		ByteArrayOutputStream fooJarStream = new ByteArrayOutputStream();
		//
		new JarOutputStream(fooJarStream).close();
		// 设置尺寸 ..
		libFoo.setSize(fooJarStream.size());
		// 使用CRC 32 保证数据正确性 ...
		CRC32 crc32 = new CRC32();
		// 校验 ..
		crc32.update(fooJarStream.toByteArray());
		// 设置crc ...
		libFoo.setCrc(crc32.getValue());
		// 设置libFoo
		jarOutputStream.putNextEntry(libFoo);

		// 写入数据 .. 这个Entry 的内容 ...
		jarOutputStream.write(fooJarStream.toByteArray());
	}


	// 游离
	protected File explode(File archive) throws IOException {
		File exploded = new File(this.tempDir, "exploded");
		exploded.mkdirs();
		JarFile jarFile = new JarFile(archive);
		Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			File entryFile = new File(exploded, entry.getName());
			if (entry.isDirectory()) {
				entryFile.mkdirs();
			}
			else {
				FileCopyUtils.copy(jarFile.getInputStream(entry), new FileOutputStream(entryFile));
			}
		}
		jarFile.close();
		return exploded;
	}

	protected Set<URL> getUrls(List<Archive> archives) throws MalformedURLException {
		Set<URL> urls = new LinkedHashSet<>(archives.size());
		for (Archive archive : archives) {
			urls.add(archive.getUrl());
		}
		return urls;
	}

	protected final URL toUrl(File file) {
		try {
			return file.toURI().toURL();
		}
		catch (MalformedURLException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
