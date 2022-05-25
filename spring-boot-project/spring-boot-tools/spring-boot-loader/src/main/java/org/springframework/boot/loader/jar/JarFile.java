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

package org.springframework.boot.loader.jar;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.security.Permission;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;

import org.springframework.boot.loader.data.RandomAccessData;
import org.springframework.boot.loader.data.RandomAccessDataFile;

/**
 * Extended variant of {@link java.util.jar.JarFile} that behaves in the same way but
 * offers the following additional functionality.
 * <ul>
 * <li>A nested {@link JarFile} can be {@link #getNestedJarFile(ZipEntry) obtained} based
 * on any directory entry.</li>
 * <li>A nested {@link JarFile} can be {@link #getNestedJarFile(ZipEntry) obtained} for
 * embedded JAR files (as long as their entry is not compressed).</li>
 * </ul>
 *
 * java.util.jar.JarFile的一个扩展变种 - 能够提供额外的功能(在某种形式下)
 * - 一个内嵌的JarFile 能够是一个目录条目 {@link #getNestedJarFile(ZipEntry)}
 * - 一个内嵌的JarFile 能够是内嵌的Jar files ...(只要它们的条目没有被压缩)
 *
 *
 *
 * 这里使用的设计模式为   重叠构造器模式
 * 框架中大量使用这种
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class JarFile extends AbstractJarFile implements Iterable<java.util.jar.JarEntry> {

	private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

	// java 的协议处理器的包前缀
	private static final String PROTOCOL_HANDLER = "java.protocol.handler.pkgs";

	//spring boot 提供的Handler 包 前缀 ..
	private static final String HANDLERS_PACKAGE = "org.springframework.boot.loader";

	private static final AsciiBytes META_INF = new AsciiBytes("META-INF/");

	// 签名文件后缀 ..
	private static final AsciiBytes SIGNATURE_FILE_EXTENSION = new AsciiBytes(".SF");

	private static final String READ_ACTION = "read";

	private final RandomAccessDataFile rootFile;

	// 根路径
	private final String pathFromRoot;

	// 一个压缩文件的核心内容信息
	private final RandomAccessData data;

	private final JarFileType type;

	private URL url;

	private String urlString;

	private JarFileEntries entries;

	private Supplier<Manifest> manifestSupplier;

	private SoftReference<Manifest> manifest;

	private boolean signed;

	// 指定 注释 ..内容
	private String comment;

	private volatile boolean closed;

	private volatile JarFileWrapper wrapper;

	/**
	 * Create a new {@link JarFile} backed by the specified file.
	 * 创建一个 新的JarFile 文件,背后使用随机访问机制
	 * @param file the root jar file
	 * @throws IOException if the file cannot be read
	 */
	public JarFile(File file) throws IOException {
		this(new RandomAccessDataFile(file));
	}

	/**
	 * Create a new {@link JarFile} backed by the specified file.
	 * @param file the root jar file
	 * @throws IOException if the file cannot be read
	 */
	JarFile(RandomAccessDataFile file) throws IOException {
		this(file, "", file, JarFileType.DIRECT);
	}

	/**
	 * Private constructor used to create a new {@link JarFile} either directly or from a
	 * nested entry.
	 * 一个私有的构造器 被用来要么直接创建或者依据一个内嵌的条目创建新的JarFile ...
	 * @param rootFile the root jar file
	 * @param pathFromRoot the name of this file
	 * @param data the underlying data
	 * @param type the type of the jar file   jarfile 的类型
	 * @throws IOException if the file cannot be read
	 */
	private JarFile(RandomAccessDataFile rootFile, String pathFromRoot, RandomAccessData data, JarFileType type)
			throws IOException {
		this(rootFile, pathFromRoot, data, null, type, null);
	}

	// core 构造
	private JarFile(RandomAccessDataFile rootFile, String pathFromRoot, RandomAccessData data, JarEntryFilter filter,
			JarFileType type, Supplier<Manifest> manifestSupplier) throws IOException {

		// 从指定的文件中创建一个ZIPFile 如果Jar 签名,会进行验证
		super(rootFile.getFile());
		// 如果系统的安全管理器为空 .. 一般来说 系统的安全管理器不会设置(普通jar)
		if (System.getSecurityManager() == null) {
			// 执行文件资源关闭 ...
			super.close();
		}
		this.rootFile = rootFile;
		this.pathFromRoot = pathFromRoot;
		CentralDirectoryParser parser = new CentralDirectoryParser();
		this.entries = parser.addVisitor(new JarFileEntries(this, filter));
		//JarFile Type
		this.type = type;
		// 增加一个中央目录仓库访问器
		parser.addVisitor(centralDirectoryVisitor());
		try {
			this.data = parser.parse(data, filter == null);
		}
		catch (RuntimeException ex) {
			try {
				this.rootFile.close();
				super.close();
			}
			catch (IOException ioex) {
			}
			throw ex;
		}

		// 默认清单提供器  读取清单文件 ...
		// 如果有则返回,否则null 否则报错 ...
		this.manifestSupplier = (manifestSupplier != null) ? manifestSupplier : () -> {
			try (InputStream inputStream = getInputStream(MANIFEST_NAME)) {
				if (inputStream == null) {
					return null;
				}
				return new Manifest(inputStream);
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		};
	}

	private CentralDirectoryVisitor centralDirectoryVisitor() {
		return new CentralDirectoryVisitor() {

			@Override
			public void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) {
				JarFile.this.comment = endRecord.getComment();
			}

			@Override
			public void visitFileHeader(CentralDirectoryFileHeader fileHeader, long dataOffset) {
				AsciiBytes name = fileHeader.getName();
				if (name.startsWith(META_INF) && name.endsWith(SIGNATURE_FILE_EXTENSION)) {
					// 表示Jar 被签名了 ...
					JarFile.this.signed = true;
				}
			}

			@Override
			public void visitEnd() {
			}

		};
	}

	JarFileWrapper getWrapper() throws IOException {
		JarFileWrapper wrapper = this.wrapper;
		if (wrapper == null) {
			wrapper = new JarFileWrapper(this);
			this.wrapper = wrapper;
		}
		return wrapper;
	}

	@Override
	Permission getPermission() {
		return new FilePermission(this.rootFile.getFile().getPath(), READ_ACTION);
	}

	protected final RandomAccessDataFile getRootJarFile() {
		return this.rootFile;
	}

	RandomAccessData getData() {
		return this.data;
	}

	@Override
	public Manifest getManifest() throws IOException {
		Manifest manifest = (this.manifest != null) ? this.manifest.get() : null;
		if (manifest == null) {
			try {
				manifest = this.manifestSupplier.get();
			}
			catch (RuntimeException ex) {
				throw new IOException(ex);
			}
			this.manifest = new SoftReference<>(manifest);
		}
		return manifest;
	}

	@Override
	public Enumeration<java.util.jar.JarEntry> entries() {
		return new JarEntryEnumeration(this.entries.iterator());
	}

	@Override
	public Stream<java.util.jar.JarEntry> stream() {
		Spliterator<java.util.jar.JarEntry> spliterator = Spliterators.spliterator(iterator(), size(),
				Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL);
		return StreamSupport.stream(spliterator, false);
	}

	/**
	 * Return an iterator for the contained entries.
	 * @since 2.3.0
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Iterator<java.util.jar.JarEntry> iterator() {
		return (Iterator) this.entries.iterator(this::ensureOpen);
	}

	public JarEntry getJarEntry(CharSequence name) {
		return this.entries.getEntry(name);
	}

	@Override
	public JarEntry getJarEntry(String name) {
		return (JarEntry) getEntry(name);
	}

	public boolean containsEntry(String name) {
		return this.entries.containsEntry(name);
	}

	@Override
	public ZipEntry getEntry(String name) {
		ensureOpen();
		return this.entries.getEntry(name);
	}

	@Override
	InputStream getInputStream() throws IOException {
		return this.data.getInputStream();
	}

	@Override
	public synchronized InputStream getInputStream(ZipEntry entry) throws IOException {
		ensureOpen();
		if (entry instanceof JarEntry) {
			return this.entries.getInputStream((JarEntry) entry);
		}
		return getInputStream((entry != null) ? entry.getName() : null);
	}

	InputStream getInputStream(String name) throws IOException {
		return this.entries.getInputStream(name);
	}

	/**
	 * Return a nested {@link JarFile} loaded from the specified entry.
	 *
	 * 从指定的Entry 中获取内嵌的JarFile
	 * @param entry the zip entry
	 * @return a {@link JarFile} for the entry
	 * @throws IOException if the nested jar file cannot be read
	 */
	public synchronized JarFile getNestedJarFile(ZipEntry entry) throws IOException {
		return getNestedJarFile((JarEntry) entry);
	}

	/**
	 * Return a nested {@link JarFile} loaded from the specified entry.
	 * @param entry the zip entry
	 * @return a {@link JarFile} for the entry
	 * @throws IOException if the nested jar file cannot be read
	 */
	public synchronized JarFile getNestedJarFile(JarEntry entry) throws IOException {
		try {
			return createJarFileFromEntry(entry);
		}
		catch (Exception ex) {
			throw new IOException("Unable to open nested jar file '" + entry.getName() + "'", ex);
		}
	}

	private JarFile createJarFileFromEntry(JarEntry entry) throws IOException {
		// 如果是一个目录
		if (entry.isDirectory()) {
			// 根据目录Entry 创建JarFile ...
			return createJarFileFromDirectoryEntry(entry);
		}
		return createJarFileFromFileEntry(entry);
	}

	private JarFile createJarFileFromDirectoryEntry(JarEntry entry) throws IOException {
		AsciiBytes name = entry.getAsciiBytesName();
		JarEntryFilter filter = (candidate) -> {
			if (candidate.startsWith(name) && !candidate.equals(name)) {
				return candidate.substring(name.length());
			}
			return null;
		};
		// 返回一个新的JarFile ..(此时它从root开始的路径就是 ... ...+"!/" + ...
		// 之所以 - 1 是因为目录 ...
		// 表示它的JarFile 类型为内嵌的目录 ...
		return new JarFile(this.rootFile, this.pathFromRoot + "!/" + entry.getName().substring(0, name.length() - 1),
				this.data, filter, JarFileType.NESTED_DIRECTORY, this.manifestSupplier);
	}

	// 从文件Entry 中创建JarFile ...
	private JarFile createJarFileFromFileEntry(JarEntry entry) throws IOException {
		// 如果方法不是STORED
		// 这里可以知道Spring 必须包含没有被压缩过的内嵌entry ...
		if (entry.getMethod() != ZipEntry.STORED) {
			throw new IllegalStateException(
					"Unable to open nested entry '" + entry.getName() + "'. It has been compressed and nested "
							+ "jar files must be stored without compression. Please check the "
							+ "mechanism used to create your executable jar file");
		}

		//根据entries 获取 获取entry Data(这个内嵌jar的信息)
		RandomAccessData entryData = this.entries.getEntryData(entry.getName());
		// 创建一个新的JarFile....
		return new JarFile(this.rootFile, this.pathFromRoot + "!/" + entry.getName(), entryData,
				JarFileType.NESTED_JAR);
	}

	@Override
	public String getComment() {
		ensureOpen();
		return this.comment;
	}

	@Override
	public int size() {
		ensureOpen();
		return this.entries.getSize();
	}

	@Override
	public void close() throws IOException {
		if (this.closed) {
			return;
		}
		super.close();
		if (this.type == JarFileType.DIRECT) {
			this.rootFile.close();
		}
		this.closed = true;
	}

	private void ensureOpen() {
		if (this.closed) {
			throw new IllegalStateException("zip file closed");
		}
	}

	boolean isClosed() {
		return this.closed;
	}

	String getUrlString() throws MalformedURLException {
		if (this.urlString == null) {
			this.urlString = getUrl().toString();
		}
		return this.urlString;
	}

	@Override
	public URL getUrl() throws MalformedURLException {
		if (this.url == null) {
			String file = this.rootFile.getFile().toURI() + this.pathFromRoot + "!/";
			file = file.replace("file:////", "file://"); // Fix UNC paths
			this.url = new URL("jar", "", -1, file, new Handler(this));
		}
		return this.url;
	}

	@Override
	public String toString() {
		return getName();
	}

	@Override
	public String getName() {
		return this.rootFile.getFile() + this.pathFromRoot;
	}

	boolean isSigned() {
		return this.signed;
	}

	JarEntryCertification getCertification(JarEntry entry) {
		try {
			return this.entries.getCertification(entry);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public void clearCache() {
		this.entries.clearCache();
	}

	protected String getPathFromRoot() {
		return this.pathFromRoot;
	}

	@Override
	JarFileType getType() {
		return this.type;
	}

	/**
	 * Register a {@literal 'java.protocol.handler.pkgs'} property so that a
	 * {@link URLStreamHandler} will be located to deal with jar URLs.
	 *
	 * 注册一个'java.protocol.handler.pkgs' 属性对应的URLStreamHandler 被用来 定位处理jar URL ....
	 */
	public static void registerUrlProtocolHandler() {
		Handler.captureJarContextUrl();
		String handlers = System.getProperty(PROTOCOL_HANDLER, "");
		// 追加 ...
		System.setProperty(PROTOCOL_HANDLER,
				((handlers == null || handlers.isEmpty()) ? HANDLERS_PACKAGE : handlers + "|" + HANDLERS_PACKAGE));
		// 重置 ...
		resetCachedUrlHandlers();
	}

	/**
	 * Reset any cached handlers just in case a jar protocol has already been used. We
	 * reset the handler by trying to set a null {@link URLStreamHandlerFactory} which
	 * should have no effect other than clearing the handlers cache.
	 */
	private static void resetCachedUrlHandlers() {
		try {
			URL.setURLStreamHandlerFactory(null);
		}
		catch (Error ex) {
			// Ignore
		}
	}

	/**
	 * An {@link Enumeration} on {@linkplain java.util.jar.JarEntry jar entries}.
	 */
	private static class JarEntryEnumeration implements Enumeration<java.util.jar.JarEntry> {

		private final Iterator<JarEntry> iterator;

		JarEntryEnumeration(Iterator<JarEntry> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasMoreElements() {
			return this.iterator.hasNext();
		}

		@Override
		public java.util.jar.JarEntry nextElement() {
			return this.iterator.next();
		}

	}

}
