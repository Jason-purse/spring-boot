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

package org.springframework.boot.loader.jar;

import java.util.Objects;

/**
 * A {@link CharSequence} backed by a single shared {@link String}. Unlike a regular
 * {@link String}, {@link #subSequence(int, int)} operations will not copy the underlying
 * character array.
 *
 * 由单个共享的字符串 支持的CharSequence .. 不像普通的常规字符串
 * 这里的 .. subSequence ... 操作不会复制底层的字符串数组 ...
 *
 * 它是共享同一个字符串  ..
 *
 * @author Phillip Webb
 */
final class StringSequence implements CharSequence {

	private final String source;

	private final int start;

	private final int end;

	private int hash;

	StringSequence(String source) {
		this(source, 0, (source != null) ? source.length() : -1);
	}

	StringSequence(String source, int start, int end) {
		Objects.requireNonNull(source, "Source must not be null");
		if (start < 0) {
			throw new StringIndexOutOfBoundsException(start);
		}
		if (end > source.length()) {
			throw new StringIndexOutOfBoundsException(end);
		}
		this.source = source;
		this.start = start;
		this.end = end;
	}

	StringSequence subSequence(int start) {
		return subSequence(start, length());
	}

	@Override
	public StringSequence subSequence(int start, int end) {
		int subSequenceStart = this.start + start;
		int subSequenceEnd = this.start + end;
		if (subSequenceStart > this.end) {
			throw new StringIndexOutOfBoundsException(start);
		}
		if (subSequenceEnd > this.end) {
			throw new StringIndexOutOfBoundsException(end);
		}
		if (start == 0 && subSequenceEnd == this.end) {
			return this;
		}

		// 字符串序列 ... 一个新的(一个字符串窗口 内容)
		return new StringSequence(this.source, subSequenceStart, subSequenceEnd);
	}

	/**
	 * Returns {@code true} if the sequence is empty. Public to be compatible with JDK 15.
	 * @return {@code true} if {@link #length()} is {@code 0}, otherwise {@code false}
	 */
	public boolean isEmpty() {
		return length() == 0;
	}

	@Override
	public int length() {
		return this.end - this.start;
	}

	@Override
	public char charAt(int index) {
		return this.source.charAt(this.start + index);
	}

	int indexOf(char ch) {
		return this.source.indexOf(ch, this.start) - this.start;
	}

	int indexOf(String str) {
		return this.source.indexOf(str, this.start) - this.start;
	}

	int indexOf(String str, int fromIndex) {
		return this.source.indexOf(str, this.start + fromIndex) - this.start;
	}

	boolean startsWith(String prefix) {
		return startsWith(prefix, 0);
	}

	boolean startsWith(String prefix, int offset) {
		int prefixLength = prefix.length();
		int length = length();
		if (length - prefixLength - offset < 0) {
			return false;
		}
		return this.source.startsWith(prefix, this.start + offset);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof CharSequence)) {
			return false;
		}
		CharSequence other = (CharSequence) obj;
		int n = length();
		if (n != other.length()) {
			return false;
		}
		int i = 0;
		while (n-- != 0) {
			if (charAt(i) != other.charAt(i)) {
				return false;
			}
			i++;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hash = this.hash;
		if (hash == 0 && length() > 0) {
			for (int i = this.start; i < this.end; i++) {
				hash = 31 * hash + this.source.charAt(i);
			}
			this.hash = hash;
		}
		return hash;
	}

	@Override
	public String toString() {
		return this.source.substring(this.start, this.end);
	}

}
