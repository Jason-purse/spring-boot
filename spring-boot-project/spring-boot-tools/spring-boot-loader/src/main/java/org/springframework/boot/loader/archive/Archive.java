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

package org.springframework.boot.loader.archive;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.jar.Manifest;

import org.springframework.boot.loader.Launcher;

/**
 * An archive that can be launched by the {@link Launcher}.
 *
 * Launcher 启动的archive ..
 *
 *
 * @author Phillip Webb
 * @since 1.0.0
 * @see JarFileArchive
 */
public interface Archive extends Iterable<Archive.Entry>, AutoCloseable {

	/**
	 * 加载这个archive的URL
	 * Returns a URL that can be used to load the archive.
	 * @return the archive URL
	 * @throws MalformedURLException if the URL is malformed
	 */
	URL getUrl() throws MalformedURLException;

	/**
	 * Returns the manifest of the archive.
	 * @return the manifest
	 * @throws IOException if the manifest cannot be read
	 */
	Manifest getManifest() throws IOException;

	/**
	 * Returns nested {@link Archive}s for entries that match the specified filters.
	 * @param searchFilter filter used to limit when additional sub-entry searching is
	 * required or {@code null} if all entries should be considered.
	 * @param includeFilter filter used to determine which entries should be included in
	 * the result or {@code null} if all entries should be included
	 *
	 * 返回内嵌的Archive(根据匹配指定的过滤器的entry)
	 *
	 *  过滤器用来限制 在sub-entry查询中额外的限制 ...
	 *  includeFilter 被用来决定 entries 应该包括在 结果中 或者 如果为null 全部包括 ...
	 * @return the nested archives
	 * @throws IOException on IO error
	 * @since 2.3.0
	 */
	default Iterator<Archive> getNestedArchives(EntryFilter searchFilter, EntryFilter includeFilter)
			throws IOException {
		EntryFilter combinedFilter = (entry) -> (searchFilter == null || searchFilter.matches(entry))
				&& (includeFilter == null || includeFilter.matches(entry));
		List<Archive> nestedArchives = getNestedArchives(combinedFilter);
		return nestedArchives.iterator();
	}

	/**
	 * Returns nested {@link Archive}s for entries that match the specified filter.
	 *
	 * 根据指定 过滤器获取内嵌的Archive ...
	 * @param filter the filter used to limit entries
	 * @return nested archives
	 * @throws IOException if nested archives cannot be read
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of
	 * {@link #getNestedArchives(EntryFilter, EntryFilter)}
	 */
	@Deprecated
	default List<Archive> getNestedArchives(EntryFilter filter) throws IOException {
		throw new IllegalStateException("Unexpected call to getNestedArchives(filter)");
	}

	/**
	 * Return a new iterator for the archive entries.
	 *
	 * 为archive entries 返回一个新的iterator
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of using
	 * @see java.lang.Iterable#iterator()
	 * {@link org.springframework.boot.loader.jar.JarFile} to access entries and
	 * {@link #getNestedArchives(EntryFilter, EntryFilter)} for accessing nested archives.
	 */
	@Deprecated
	@Override
	Iterator<Entry> iterator();

	/**
	 * Performs the given action for each element of the {@code Iterable} until all
	 * elements have been processed or the action throws an exception.
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of using
	 * @see Iterable#forEach {@link org.springframework.boot.loader.jar.JarFile} to access
	 * entries and {@link #getNestedArchives(EntryFilter, EntryFilter)} for accessing
	 * nested archives.
	 */
	@Deprecated
	@Override
	default void forEach(Consumer<? super Entry> action) {
		Objects.requireNonNull(action);
		for (Entry entry : this) {
			action.accept(entry);
		}
	}

	/**
	 * Creates a {@link Spliterator} over the elements described by this {@code Iterable}.
	 * @deprecated since 2.3.0 for removal in 2.5.0 in favor of using
	 * @see Iterable#spliterator {@link org.springframework.boot.loader.jar.JarFile} to
	 * access entries and {@link #getNestedArchives(EntryFilter, EntryFilter)} for
	 * accessing nested archives.
	 */
	@Deprecated
	@Override
	default Spliterator<Entry> spliterator() {
		return Spliterators.spliteratorUnknownSize(iterator(), 0);
	}

	/**
	 * 返回这个archive 是暴露的(含义是否解压了的)
	 * Return if the archive is exploded (already unpacked).
	 * @return if the archive is exploded
	 * @since 2.3.0
	 */
	default boolean isExploded() {
		return false;
	}

	/**
	 * 关闭这个Archive  释放任何打开的资源
	 * Closes the {@code Archive}, releasing any open resources.
	 * @throws Exception if an error occurs during close processing
	 * @since 2.2.0
	 */
	@Override
	default void close() throws Exception {

	}

	/**
	 * Represents a single entry in the archive.
	 *
	 * 表示archive中的一个条目 ..
	 */
	interface Entry {

		/**
		 * Returns {@code true} if the entry represents a directory.
		 * @return if the entry is a directory
		 */
		boolean isDirectory();

		/**
		 * Returns the name of the entry.
		 * @return the name of the entry
		 */
		String getName();

	}

	/**
	 * Strategy interface to filter {@link Entry Entries}.
	 *
	 * Entries 的过滤器策略接口 ...
	 */
	@FunctionalInterface
	interface EntryFilter {

		/**
		 * Apply the jar entry filter.
		 * @param entry the entry to filter
		 * @return {@code true} if the filter matches
		 */
		boolean matches(Entry entry);

	}

}
