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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.loader.data.RandomAccessData;

/**
 * Parses the central directory from a JAR file.
 * 解析一个Jar file 的核心目录的解析器 ,期间有各种回调 ...
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @see CentralDirectoryVisitor
 */
class CentralDirectoryParser {

	private static final int CENTRAL_DIRECTORY_HEADER_BASE_SIZE = 46;

	private final List<CentralDirectoryVisitor> visitors = new ArrayList<>();

	<T extends CentralDirectoryVisitor> T addVisitor(T visitor) {
		this.visitors.add(visitor);
		return visitor;
	}

	/**
	 * Parse the source data, triggering {@link CentralDirectoryVisitor visitors}.
	 * @param data the source data
	 * @param skipPrefixBytes if prefix bytes should be skipped(前缀字节是否应该跳过)
	 * @return the actual archive data without any prefix bytes
	 * @throws IOException on error
	 */
	RandomAccessData parse(RandomAccessData data, boolean skipPrefixBytes) throws IOException {
		// 算出中央目录记录结束信息
		CentralDirectoryEndRecord endRecord = new CentralDirectoryEndRecord(data);
		// 是否跳过前缀字节 ... 不知道什么意思.
		if (skipPrefixBytes) {
			data = getArchiveData(endRecord, data);
		}
		// 然后
		RandomAccessData centralDirectoryData = endRecord.getCentralDirectory(data);
		// 开始浏览 ..
		visitStart(endRecord, centralDirectoryData);
		// 解析每一个条目
		parseEntries(endRecord, centralDirectoryData);
		// 浏览结束
		visitEnd();
		return data;
	}

	// 解析条目的核心方法 ...
	private void parseEntries(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData)
			throws IOException {
		// 中央目录数据
		byte[] bytes = centralDirectoryData.read(0, centralDirectoryData.getSize());
		// 中央目录文件头 ..
		CentralDirectoryFileHeader fileHeader = new CentralDirectoryFileHeader();
		int dataOffset = 0;
		for (int i = 0; i < endRecord.getNumberOfRecords(); i++) {
			//
			fileHeader.load(bytes, dataOffset, null, 0, null);
			visitFileHeader(dataOffset, fileHeader);

			// https://en.wikipedia.org/wiki/ZIP_(file_format) #Central directory file header
			//46	n	File name
			//46+n	m	Extra field
			//46+n+m	k	File comment
			// 数据偏距进行 添加(包括  注释 / 额外字段 文件名的 长度)
			dataOffset += CENTRAL_DIRECTORY_HEADER_BASE_SIZE + fileHeader.getName().length()
					+ fileHeader.getComment().length() + fileHeader.getExtra().length;
		}
	}

	// 获取归档数据
	private RandomAccessData getArchiveData(CentralDirectoryEndRecord endRecord, RandomAccessData data) {
		// 获取归档数据开始偏距 .. 如果为 0
		long offset = endRecord.getStartOfArchive(data);
		if (offset == 0) {
			return data;
		}
		return data.getSubsection(offset, data.getSize() - offset);
	}

	private void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData) {
		for (CentralDirectoryVisitor visitor : this.visitors) {
			visitor.visitStart(endRecord, centralDirectoryData);
		}
	}

	private void visitFileHeader(long dataOffset, CentralDirectoryFileHeader fileHeader) {
		for (CentralDirectoryVisitor visitor : this.visitors) {
			visitor.visitFileHeader(fileHeader, dataOffset);
		}
	}

	private void visitEnd() {
		for (CentralDirectoryVisitor visitor : this.visitors) {
			visitor.visitEnd();
		}
	}

}
