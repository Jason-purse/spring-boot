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

import org.springframework.boot.loader.data.RandomAccessData;

/**
 * A ZIP File "End of central directory record" (EOCD).
 * 一个ZIP 文件 中央目录记录结束 (EOCD)
 *
 * 正确读取 ZIP 档案的工具必须扫描中央目录记录签名的结尾，然后酌情扫描其他指示的中央目录记录。他们不能从 ZIP 文件的顶部扫描条目，因为（如本节前面提到的）只有中央目录指定文件块的开始位置并且它没有被删除。
 * 扫描可能会导致误报，因为该格式不禁止其他数据位于块之间，也不禁止文件数据流包含此类签名。
 * 但是，试图从损坏的 ZIP 存档中恢复数据的工具很可能会扫描存档以查找本地文件头签名；由于文件块的压缩大小可能存储在文件块之后，这使得顺序处理变得困难，这使得这变得更加困难。
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Camille Vienot
 * @see <a href="https://en.wikipedia.org/wiki/Zip_%28file_format%29">Zip File Format</a>
 */
class CentralDirectoryEndRecord {

	// 如果没有COMMENT 最小中央目录记录结束尺寸 22
	private static final int MINIMUM_SIZE = 22;

	// 2个字节 ...
	// 16进制 4位二进制一个16进制字符
	private static final int MAXIMUM_COMMENT_LENGTH = 0xFFFF;

	// 最大长度 就是加上注释的长度 ..
	private static final int MAXIMUM_SIZE = MINIMUM_SIZE + MAXIMUM_COMMENT_LENGTH;

	// 大多数签名以短整数 0x4b50 结尾，以小端序存储。作为一个ASCII字符串，它读作“PK”，即发明者 Phil Katz 的首字母。因此，当在文本编辑器中查看 ZIP 文件时，文件的前两个字节通常是“PK”。
	// End of central directory signature = 0x06054b50
	private static final int SIGNATURE = 0x06054b50;

	// 中间目录记录结束中  Comment 的偏距 指定为 20字节  ..
	private static final int COMMENT_LENGTH_OFFSET = 20;

	// 读取最小块大小
	private static final int READ_BLOCK_SIZE = 256;

	// zip 64 相关的 ...
	private final Zip64End zip64End;

	private byte[] block;

	//字节偏距
	private int offset;

	// 这个中央目录记录最终尺寸
	private int size;

	/**
	 * Create a new {@link CentralDirectoryEndRecord} instance from the specified
	 * {@link RandomAccessData}, searching backwards from the end until a valid block is
	 * located.
	 *
	 * 从文件结束反向查询,直到一个有效的块被获取到 ...
	 * @param data the source data
	 * @throws IOException in case of I/O errors
	 */
	CentralDirectoryEndRecord(RandomAccessData data) throws IOException {
		// 从数据结尾处创建一个 block ..
		this.block = createBlockFromEndOfData(data, READ_BLOCK_SIZE);
		this.size = MINIMUM_SIZE;
		// 指定目前的偏距 ...
		this.offset = this.block.length - this.size;
		while (!isValid()) {
			// 开始计算有效数据 ...
			// 每次走一个字节 ...
			this.size++;
			if (this.size > this.block.length) {
				// 判断size 大于中央目录记录尺寸,那么是有问题的 ... 因为最大只有 MAXIMUM_SIZE ...
				// 那么可能文件是损坏的 ...
				if (this.size >= MAXIMUM_SIZE || this.size > data.getSize()) {
					throw new IOException(
							"Unable to find ZIP central directory records after reading " + this.size + " bytes");
				}
				// 否则 重新创建一次 ...
				this.block = createBlockFromEndOfData(data, this.size + READ_BLOCK_SIZE);
			}
			this.offset = this.block.length - this.size;
		}

		// 用文件的总尺寸 - 当前中央目录尺寸 = 开始索引
		long startOfCentralDirectoryEndRecord = data.getSize() - this.size;
		// 然后是可选的“zip64”目录条目
		// 本地文件头 (LOC) 和中央目录条目 (CEN) 的格式在 ZIP 和 ZIP64 中是相同的。但是，ZIP64 指定了一个额外的字段，可以由压缩器自行决定添加到这些记录中，其目的是存储不适合经典 LOC 或 CEN 记录的值。
		// 为了表明实际值存储在 ZIP64 额外字段中，它们在相应的 LOC 或 CEN 记录中设置为 0xFFFF 或 0xFFFFFFFF。如果一个条目不适合经典的 LOC 或 CEN 记录，则只需将该条目移动到 ZIP64 额外字段中。
		// 其他条目可能保留在经典记录中。因此，并非下表中显示的所有条目都可能存储在 ZIP64 额外字段中。但是，如果它们出现，它们的顺序必须如表中所示。
		//
		//Zip64 扩展信息额外字段
		//offset	字节	说明[31]
		//0      	2	标头 ID 0x0001
		//2	        2	额外字段块的大小（8、16、24 或 28）
		//4	        8	原始未压缩文件大小
		//12	    8	压缩数据的大小
		//20	    8	本地头记录的偏移量
		//28	    4	此文件启动的磁盘号
		Zip64Locator zip64Locator = Zip64Locator.find(data, startOfCentralDirectoryEndRecord);
		this.zip64End = (zip64Locator != null) ? new Zip64End(data, zip64Locator) : null;
	}

	private byte[] createBlockFromEndOfData(RandomAccessData data, int size) throws IOException {
		int length = (int) Math.min(data.getSize(), size);
		return data.read(data.getSize() - length, length);
	}

	// 是否有效 ..
	private boolean isValid() {
		// 判断读取出来的block 长度是否小于 最小尺寸, 或者 小端 4字节签名
		// The ZIP format uses specific 4-byte "signatures" to denote the various structures in the file. Each file entry is marked by a specific signature.
		// The end of central directory record is indicated with its specific signature,
		// and each entry in the central directory starts with the 4-byte central file header signature.
		// 如果是 表示是一个无效的entry
		if (this.block.length < MINIMUM_SIZE || Bytes.littleEndianValue(this.block, this.offset + 0, 4) != SIGNATURE) {
			return false;
		}
		// Total size must be the structure size + comment
		// 否则总体尺寸必须等于结构数据尺寸 + comment size
		// 注释的长度只有2 字节 ..
		long commentLength = Bytes.littleEndianValue(this.block, this.offset + COMMENT_LENGTH_OFFSET, 2);
		// 判断当前的size == 最小尺寸 + commentLength
		return this.size == MINIMUM_SIZE + commentLength;
	}

	/**
	 * Returns the location in the data that the archive actually starts. For most files
	 * the archive data will start at 0, however, it is possible to have prefixed bytes
	 * (often used for startup scripts) at the beginning of the data.
	 *
	 * 返回归档数据实际开始的位置 ... 大多数归档文件的数据 从0开始,然而 它可能有一些前置 字节 ..
	 * (例如一些启动脚本的bytes) 放置在数据的前面 ...
	 * @param data the source data
	 * @return the offset within the data where the archive begins
	 */
	long getStartOfArchive(RandomAccessData data) {

		// 启动脚本的prefixed bytes 4个字节 ...
		// 这里的 长度因人而已,并且前面的12个字节是什么呢 ...
		long length = Bytes.littleEndianValue(this.block, this.offset + 12, 4);
		long specifiedOffset = (this.zip64End != null) ? this.zip64End.centralDirectoryOffset
				: Bytes.littleEndianValue(this.block, this.offset + 16, 4);
		long zip64EndSize = (this.zip64End != null) ? this.zip64End.getSize() : 0L;
		int zip64LocSize = (this.zip64End != null) ? Zip64Locator.ZIP64_LOCSIZE : 0;
		// Zip 组成:  [本地文件内容]*+[prefix exec]?+[中央目录记录]*+[中央目录记录结束]?+[中央目录记录Locator]?
		long actualOffset = data.getSize() - this.size - length - zip64EndSize - zip64LocSize;
		return actualOffset - specifiedOffset;
	}

	/**
	 * Return the bytes of the "Central directory" based on the offset indicated in this
	 * record.
	 *
	 * 基于这个record的offset 返回中央目录的内容 ...
	 * @param data the source data
	 * @return the central directory data
	 */
	RandomAccessData getCentralDirectory(RandomAccessData data) {
		// 如果是zip64 ...
		if (this.zip64End != null) {
			return this.zip64End.getCentralDirectory(data);
		}
		// 否则 ... 直接获取偏距 + 16
		// 	中央目录开始的偏移量，相对于存档的开始（或 ZIP64 的 0xffffffff）
		long offset = Bytes.littleEndianValue(this.block, this.offset + 16, 4);
		// 长度 中央目录的大小（字节）（或 ZIP64 的 0xffffffff）
		long length = Bytes.littleEndianValue(this.block, this.offset + 12, 4);
		// 返回区域信息 ...
		return data.getSubsection(offset, length);
	}

	/**
	 * Return the number of ZIP entries in the file.
	 * @return the number of records in the zip
	 */
	int getNumberOfRecords() {
		if (this.zip64End != null) {
			return this.zip64End.getNumberOfRecords();
		}
		// 获取条目数
		long numberOfRecords = Bytes.littleEndianValue(this.block, this.offset + 10, 2);
		return (int) numberOfRecords;
	}

	String getComment() {
		int commentLength = (int) Bytes.littleEndianValue(this.block, this.offset + COMMENT_LENGTH_OFFSET, 2);
		AsciiBytes comment = new AsciiBytes(this.block, this.offset + COMMENT_LENGTH_OFFSET + 2, commentLength);
		return comment.toString();
	}

	boolean isZip64() {
		return this.zip64End != null;
	}

	/**
	 * A Zip64 end of central directory record.
	 *
	 * Zip64 中央目录记录结束
	 * @see <a href="https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT">Chapter
	 * 4.3.14 of Zip64 specification</a>
	 */
	private static final class Zip64End {

		// 一个额外信息实体的总长度 2+2+8+8+8+4
		// https://en.wikipedia.org/wiki/ZIP_(file_format)  ZIP64
		private static final int ZIP64_ENDTOT = 32; // total number of entries

		//  以字节为单位的中央目录大小 后面的直到 第一个CEN header的8个字节 表示真正的size
		private static final int ZIP64_ENDSIZ = 40; // central directory size in bytes

		//offset 48	 8 byte	中央目录开始的偏移量，相对于存档的开始
		private static final int ZIP64_ENDOFF = 48; // offset of first CEN header

		private final Zip64Locator locator;

		private final long centralDirectoryOffset;

		private final long centralDirectoryLength;

		private final int numberOfRecords;

		private Zip64End(RandomAccessData data, Zip64Locator locator) throws IOException {
			this.locator = locator;
			// 获取整个中央目录的部分数据 ...
			byte[] block = data.read(locator.getZip64EndOffset(), 56);
			// 然后将偏距算出来 ...
			this.centralDirectoryOffset = Bytes.littleEndianValue(block, ZIP64_ENDOFF, 8);
			// 当前中央目录的总尺寸算出来 ...
			this.centralDirectoryLength = Bytes.littleEndianValue(block, ZIP64_ENDSIZ, 8);
			// 中央目录记录总数
			this.numberOfRecords = (int) Bytes.littleEndianValue(block, ZIP64_ENDTOT, 8);
		}

		/**
		 * Return the size of this zip 64 end of central directory record.
		 * @return size of this zip 64 end of central directory record
		 */
		private long getSize() {
			return this.locator.getZip64EndSize();
		}

		/**
		 * Return the bytes of the "Central directory" based on the offset indicated in
		 * this record.
		 * @param data the source data
		 * @return the central directory data
		 */
		private RandomAccessData getCentralDirectory(RandomAccessData data) {
			return data.getSubsection(this.centralDirectoryOffset, this.centralDirectoryLength);
		}

		/**
		 * Return the number of entries in the zip64 archive.
		 * @return the number of records in the zip
		 */
		private int getNumberOfRecords() {
			return this.numberOfRecords;
		}

	}

	/**
	 * A Zip64 end of central directory locator.
	 * Zip64 中央 目录定位器
	 * Zip64 中央目录记录结束 (EOCD64) 不一定是Zip64的结尾 ... 随后是中央目录定位符的结尾（末尾有 20 个字节）。
	 *
	 * @see <a href="https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT">Chapter
	 * 4.3.15 of Zip64 specification</a>
	 */
	private static final class Zip64Locator {

		static final int SIGNATURE = 0x07064b50;

		static final int ZIP64_LOCSIZE = 20; // locator size

		// zip64  end 的偏距 ...(相对于Zip64 中央目录结束的相对偏距),长度也是8
		static final int ZIP64_LOCOFF = 8; // offset of zip64 end

		// 真正的zip64 中央目录记录结束的偏距 / 存档的开始的偏移量
		private final long zip64EndOffset;

		private final long offset;

		private Zip64Locator(long offset, byte[] block) throws IOException {
			this.offset = offset;
			this.zip64EndOffset = Bytes.littleEndianValue(block, ZIP64_LOCOFF, 8);
		}

		/**
		 * Return the size of the zip 64 end record located by this zip64 end locator.
		 * @return size of the zip 64 end record located by this zip64 end locator
		 */
		private long getZip64EndSize() {
			return this.offset - this.zip64EndOffset;
		}

		/**
		 * Return the offset to locate {@link Zip64End}.
		 * @return offset of the Zip64 end of central directory record
		 */
		private long getZip64EndOffset() {
			return this.zip64EndOffset;
		}


		// 根据数据 以及中央目录记录结束 获取一个Zip64 locator...
		private static Zip64Locator find(RandomAccessData data, long centralDirectoryEndOffset) throws IOException {
			long offset = centralDirectoryEndOffset - ZIP64_LOCSIZE;
			// 如果大于0 表示 确实有zip64 locator ...
			if (offset >= 0) {
				byte[] block = data.read(offset, ZIP64_LOCSIZE);
				// 根据小端规则 比较,产生一个Zip64Locator ...
				if (Bytes.littleEndianValue(block, 0, 4) == SIGNATURE) {
					return new Zip64Locator(offset, block);
				}
			}
			return null;
		}

	}

}
