/*
 * Copyright 2012-2019 the original author or authors.
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

/**
 * System that allows self-contained JAR/WAR archives to be launched using
 * {@code java -jar}.
 * 允许使用java -jar 启动自包含jar /war 归档文件的 jar/war 的系统 ...
 * Archives can include nested packaged dependency JARs (there is no
 * need to create shade style jars) and are executed without unpacking. The only
 * constraint is that nested JARs must be stored in the archive uncompressed.
 *
 * 归档文件能够包括 内嵌的打包 依赖jar(这里不需要创建遮盖风格的jar)   并且它可以执行 且不需要解压 ..
 * 唯一的一个约束 是 内嵌的jar 必须存储在archive 内部(以未压缩的形式)
 *
 * 这里所说的内嵌jar (其实就是我们在spring boot 中开发的应用程序) ... 其他的依赖是自包含在这个archive中 ...
 * @see org.springframework.boot.loader.JarLauncher
 * @see org.springframework.boot.loader.WarLauncher
 */
package org.springframework.boot.loader;
