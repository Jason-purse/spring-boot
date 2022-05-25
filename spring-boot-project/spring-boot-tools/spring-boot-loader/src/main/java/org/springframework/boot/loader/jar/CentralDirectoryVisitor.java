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

import org.springframework.boot.loader.data.RandomAccessData;

/**
 * Callback visitor triggered by {@link CentralDirectoryParser}.
 * 由 CentralDirectoryParser 触发的回调访问者
 * @author Phillip Webb
 */
interface CentralDirectoryVisitor {

	// 访问开始回调
	void visitStart(CentralDirectoryEndRecord endRecord, RandomAccessData centralDirectoryData);

	// 访问文件头
	void visitFileHeader(CentralDirectoryFileHeader fileHeader, long dataOffset);

	// 访问结束回调
	void visitEnd();

}
