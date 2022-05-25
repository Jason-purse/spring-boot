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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ValueRange;

import org.springframework.boot.loader.data.RandomAccessData;

/**
 * A ZIP File "Central directory file header record" (CDFH).
 *
 * 文件头包含的一些信息...
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Dmytro Nosan
 * @see <a href="https://en.wikipedia.org/wiki/Zip_%28file_format%29">Zip File Format</a>
 */

final class CentralDirectoryFileHeader implements FileHeader {

	private static final AsciiBytes SLASH = new AsciiBytes("/");

	private static final byte[] NO_EXTRA = {};

	private static final AsciiBytes NO_COMMENT = new AsciiBytes("");

	// 整个中央目录header的总量
	private byte[] header;

	private int headerOffset;

	private AsciiBytes name;

	private byte[] extra;

	private AsciiBytes comment;

	private long localHeaderOffset;

	CentralDirectoryFileHeader() {
	}

	CentralDirectoryFileHeader(byte[] header, int headerOffset, AsciiBytes name, byte[] extra, AsciiBytes comment,
			long localHeaderOffset) {
		this.header = header;
		this.headerOffset = headerOffset;
		this.name = name;
		this.extra = extra;
		this.comment = comment;
		this.localHeaderOffset = localHeaderOffset;
	}




	/**
	 * 加载数据 ...
	 * @param data zip 数据内容
	 * @param dataOffset 数据偏距
	 * @param variableData 额外字段数据信息
	 * @param variableOffset 额外字段数据 偏距
	 * @param filter 过滤器 ...
	 * @throws IOException
	 */
	void load(byte[] data, int dataOffset, RandomAccessData variableData, long variableOffset, JarEntryFilter filter)
			throws IOException {
		// Load fixed part
		this.header = data;
		this.headerOffset = dataOffset;
		// 获取压缩的数据量
		long compressedSize = Bytes.littleEndianValue(data, dataOffset + 20, 4);
		// 未压缩的数据量
		long uncompressedSize = Bytes.littleEndianValue(data, dataOffset + 24, 4);
		// 名字长度
		long nameLength = Bytes.littleEndianValue(data, dataOffset + 28, 2);
		// 额外的字段长度
		long extraLength = Bytes.littleEndianValue(data, dataOffset + 30, 2);
		// 注释长度
		long commentLength = Bytes.littleEndianValue(data, dataOffset + 32, 2);
		// 本地header Local file header
		long localHeaderOffset = Bytes.littleEndianValue(data, dataOffset + 42, 4);
		// Load variable part
		dataOffset += 46;
		// 仅当它不为空的时候
		if (variableData != null) {
			// 从46开始读取 ...
			data = variableData.read(variableOffset + 46, nameLength + extraLength + commentLength);
			dataOffset = 0;
		}
		// 然后读取名称
		// 如果有额外字段(那么 dataOffset = 0,why)
		// 如果没有正常读取文件名称 ...
		// 很正常,如果有额外字段,data = 名称 + 额外数据+注释长度 ... 那么应该从0开始,否则从dataOffset开始 ...
		this.name = new AsciiBytes(data, dataOffset, (int) nameLength);

		// 如果filter 不为空,那么使用过滤器,它的作用是什么意思呢 ...
		// 就是重命名 entry name
		if (filter != null) {
			this.name = filter.apply(this.name);
		}

		// 先设置一番
		this.extra = NO_EXTRA;
		this.comment = NO_COMMENT;
		if (extraLength > 0) {
			// 如果大于0
			this.extra = new byte[(int) extraLength];
			// 尝试copy ...
			// 这里为什么不使用Ascii Bytes ...
			System.arraycopy(data, (int) (dataOffset + nameLength), this.extra, 0, this.extra.length);
		}
		// 同样本地header 偏距 等于
		this.localHeaderOffset = getLocalHeaderOffset(compressedSize, uncompressedSize, localHeaderOffset, this.extra);
		if (commentLength > 0) {
			// 如果注释量大于0 ...
			// 设置注释的信息 ...
			this.comment = new AsciiBytes(data, (int) (dataOffset + nameLength + extraLength), (int) commentLength);
		}
	}

	// 获取本地文件头偏距
	private long getLocalHeaderOffset(long compressedSize, long uncompressedSize, long localHeaderOffset, byte[] extra)
			throws IOException {
		// 如果不是 zip64 ...
		//本地文件头 (LOC) 和中央目录条目 (CEN) 的格式在 ZIP 和 ZIP64 中是相同的。但是，ZIP64 指定了一个额外的字段，可以由压缩器自行决定添加到这些记录中，
		// 其目的是存储不适合经典 LOC 或 CEN 记录的值。为了表明实际值存储在 ZIP64 额外字段中，它们在相应的 LOC 或 CEN 记录中设置为 0xFFFF 或 0xFFFFFFFF。
		// 如果一个条目不适合经典的 LOC 或 CEN 记录，则只需将该条目移动到 ZIP64 额外字段中。其他条目可能保留在经典记录中。因此，并非下表中显示的所有条目都可能存储在 ZIP64 额外字段中。
		if (localHeaderOffset != 0xFFFFFFFFL) {
			// 直接返回 ...
			return localHeaderOffset;
		}

		// 否则需要额外的算法 ...
		int extraOffset = 0;

		// 至少能够走到 -2 ,就算最后一个entry 长度为0
		while (extraOffset < extra.length - 2) {
			//  标头 ID 0x0001 2个字节
			int id = (int) Bytes.littleEndianValue(extra, extraOffset, 2);
			int length = (int) Bytes.littleEndianValue(extra, extraOffset, 2);
			extraOffset += 4;
			// == 1  表示开始 ... 计算之后直接返回 ...
			if (id == 1) {
				int localHeaderExtraOffset = 0;
				// 4 字节
				if (compressedSize == 0xFFFFFFFFL) {
					// 如果相等 本地文件头偏距 +4
					// 为什么 不是8嘛? 8个字节 ..
					localHeaderExtraOffset += 4;
				}
				// 4字节
				if (uncompressedSize == 0xFFFFFFFFL) {
					//未压缩 也是 .. + 4

					// 为什么,不是8嘛? 8个字节!!!!
					localHeaderExtraOffset += 4;
				}

				// 否则不是就需要
				return Bytes.littleEndianValue(extra, extraOffset + localHeaderExtraOffset, 8);
			}
			// 然后根据长度 +=,向前解析 ...
			extraOffset += length;
		}
		throw new IOException("Zip64 Extended Information Extra Field not found");
	}

	AsciiBytes getName() {
		return this.name;
	}

	@Override
	public boolean hasName(CharSequence name, char suffix) {
		return this.name.matches(name, suffix);
	}

	boolean isDirectory() {
		return this.name.endsWith(SLASH);
	}

	@Override
	public int getMethod() {
		return (int) Bytes.littleEndianValue(this.header, this.headerOffset + 10, 2);
	}

	long getTime() {
		long datetime = Bytes.littleEndianValue(this.header, this.headerOffset + 12, 4);
		return decodeMsDosFormatDateTime(datetime);
	}

	/**
	 * Decode MS-DOS Date Time details. See <a href=
	 * "https://docs.microsoft.com/en-gb/windows/desktop/api/winbase/nf-winbase-dosdatetimetofiletime">
	 * Microsoft's documentation</a> for more details of the format.
	 * @param datetime the date and time
	 * @return the date and time as milliseconds since the epoch
	 */
	private long decodeMsDosFormatDateTime(long datetime) {
		int year = getChronoValue(((datetime >> 25) & 0x7f) + 1980, ChronoField.YEAR);
		int month = getChronoValue((datetime >> 21) & 0x0f, ChronoField.MONTH_OF_YEAR);
		int day = getChronoValue((datetime >> 16) & 0x1f, ChronoField.DAY_OF_MONTH);
		int hour = getChronoValue((datetime >> 11) & 0x1f, ChronoField.HOUR_OF_DAY);
		int minute = getChronoValue((datetime >> 5) & 0x3f, ChronoField.MINUTE_OF_HOUR);
		int second = getChronoValue((datetime << 1) & 0x3e, ChronoField.SECOND_OF_MINUTE);
		return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.systemDefault()).toInstant()
				.truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
	}

	long getCrc() {
		return Bytes.littleEndianValue(this.header, this.headerOffset + 16, 4);
	}

	@Override
	public long getCompressedSize() {
		return Bytes.littleEndianValue(this.header, this.headerOffset + 20, 4);
	}

	@Override
	public long getSize() {
		return Bytes.littleEndianValue(this.header, this.headerOffset + 24, 4);
	}

	byte[] getExtra() {
		return this.extra;
	}

	boolean hasExtra() {
		return this.extra.length > 0;
	}

	AsciiBytes getComment() {
		return this.comment;
	}

	@Override
	public long getLocalHeaderOffset() {
		return this.localHeaderOffset;
	}

	@Override
	public CentralDirectoryFileHeader clone() {
		byte[] header = new byte[46];
		System.arraycopy(this.header, this.headerOffset, header, 0, header.length);
		return new CentralDirectoryFileHeader(header, 0, this.name, header, this.comment, this.localHeaderOffset);
	}

	static CentralDirectoryFileHeader fromRandomAccessData(RandomAccessData data, long offset, JarEntryFilter filter)
			throws IOException {
		CentralDirectoryFileHeader fileHeader = new CentralDirectoryFileHeader();
		byte[] bytes = data.read(offset, 46);
		fileHeader.load(bytes, 0, data, offset, filter);
		return fileHeader;
	}

	private static int getChronoValue(long value, ChronoField field) {
		ValueRange range = field.range();
		return Math.toIntExact(Math.min(Math.max(value, range.getMinimum()), range.getMaximum()));
	}

}
