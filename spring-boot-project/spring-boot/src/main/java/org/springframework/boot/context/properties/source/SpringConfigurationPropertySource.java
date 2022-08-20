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

package org.springframework.boot.context.properties.source;

import java.util.Map;
import java.util.Random;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName.Form;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.Assert;

/**
 * {@link ConfigurationPropertySource} backed by a non-enumerable Spring
 * {@link PropertySource} or a restricted {@link EnumerablePropertySource} implementation
 * (such as a security restricted {@code systemEnvironment} source). A
 * {@link PropertySource} is adapted with the help of a {@link PropertyMapper} which
 * provides the mapping rules for individual properties.
 * <p>
 * Each {@link ConfigurationPropertySource#getConfigurationProperty
 * getConfigurationProperty} call attempts to
 * {@link PropertyMapper#map(ConfigurationPropertyName) map} the
 * {@link ConfigurationPropertyName} to one or more {@code String} based names. This
 * allows fast property resolution for well formed property sources.
 * <p>
 * When possible the {@link SpringIterableConfigurationPropertySource} will be used in
 * preference to this implementation since it supports full "relaxed" style resolution.
 *
 *  一个被 不可枚举的Spring PropertySource或者 一个受限的EnumerablePropertySource(例如 一个安全受限的systemEnvironment资源) 实现支持
 *  一个PropertySource 通过PropertyMapper 进行映射(提供了对单个属性的单独映射规则) ...
 *
 *  ConfigurationPropertySource#getConfigurationProperty 调用将尝试基于名称 一对一映射
 *  通过 PropertyMapper#map(ConfigurationPropertyName) -> 到一个或者多个 ... 基于名称的字符串 ..
 *  者允许快速的属性解析(对于形式良好的属性源) ..
 *
 *  当必要时,SpringIterableConfigurationPropertySource 更优先于此实现,因为它支持 完全松散的风格 解析 ..
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @see #from(PropertySource)
 * @see PropertyMapper
 * @see SpringIterableConfigurationPropertySource
 */
class SpringConfigurationPropertySource implements ConfigurationPropertySource {

	private static final PropertyMapper[] DEFAULT_MAPPERS = { DefaultPropertyMapper.INSTANCE };

	private static final PropertyMapper[] SYSTEM_ENVIRONMENT_MAPPERS = { SystemEnvironmentPropertyMapper.INSTANCE,
			DefaultPropertyMapper.INSTANCE };

	private final PropertySource<?> propertySource;

	private final PropertyMapper[] mappers;

	/**
	 * Create a new {@link SpringConfigurationPropertySource} implementation.
	 * @param propertySource the source property source
	 * @param mappers the property mappers
	 */
	SpringConfigurationPropertySource(PropertySource<?> propertySource, PropertyMapper... mappers) {
		Assert.notNull(propertySource, "PropertySource must not be null");
		Assert.isTrue(mappers.length > 0, "Mappers must contain at least one item");
		this.propertySource = propertySource;
		this.mappers = mappers;
	}

	@Override
	public ConfigurationProperty getConfigurationProperty(ConfigurationPropertyName name) {
		if (name == null) {
			return null;
		}
		for (PropertyMapper mapper : this.mappers) {
			try {
				for (String candidate : mapper.map(name)) {
					Object value = getPropertySource().getProperty(candidate);
					if (value != null) {
						Origin origin = PropertySourceOrigin.get(this.propertySource, candidate);
						return ConfigurationProperty.of(this, name, value, origin);
					}
				}
			}
			catch (Exception ex) {
			}
		}
		return null;
	}

	@Override
	public ConfigurationPropertyState containsDescendantOf(ConfigurationPropertyName name) {
		PropertySource<?> source = getPropertySource();
		if (source.getSource() instanceof Random) {
			return containsDescendantOfForRandom("random", name);
		}
		if (source.getSource() instanceof PropertySource<?>
				&& ((PropertySource<?>) source.getSource()).getSource() instanceof Random) {
			// Assume wrapped random sources use the source name as the prefix
			return containsDescendantOfForRandom(source.getName(), name);
		}
		return ConfigurationPropertyState.UNKNOWN;
	}

	private static ConfigurationPropertyState containsDescendantOfForRandom(String prefix,
			ConfigurationPropertyName name) {
		if (name.getNumberOfElements() > 1 && name.getElement(0, Form.DASHED).equals(prefix)) {
			return ConfigurationPropertyState.PRESENT;
		}
		return ConfigurationPropertyState.ABSENT;
	}

	@Override
	public Object getUnderlyingSource() {
		return this.propertySource;
	}

	protected PropertySource<?> getPropertySource() {
		return this.propertySource;
	}

	protected final PropertyMapper[] getMappers() {
		return this.mappers;
	}

	@Override
	public String toString() {
		return this.propertySource.toString();
	}

	/**
	 * Create a new {@link SpringConfigurationPropertySource} for the specified
	 * {@link PropertySource}.
	 * @param source the source Spring {@link PropertySource}
	 * @return a {@link SpringConfigurationPropertySource} or
	 * {@link SpringIterableConfigurationPropertySource} instance
	 */
	static SpringConfigurationPropertySource from(PropertySource<?> source) {
		Assert.notNull(source, "Source must not be null");
		// 获取 从 source 中获取属性 mapper ..
		PropertyMapper[] mappers = getPropertyMappers(source);
		// 如果是可枚举的 ... 所以首选  SpringIterableConfigurationPropertySource
		if (isFullEnumerable(source)) {
			return new SpringIterableConfigurationPropertySource((EnumerablePropertySource<?>) source, mappers);
		}
		// 如果对于不完全枚举的,获取不到,直接返回 null ..
		return new SpringConfigurationPropertySource(source, mappers);
	}

	private static PropertyMapper[] getPropertyMappers(PropertySource<?> source) {
		// 如果 为系统环境属性PropertySource ...
		if (source instanceof SystemEnvironmentPropertySource && hasSystemEnvironmentName(source)) {
			return SYSTEM_ENVIRONMENT_MAPPERS;
		}
		return DEFAULT_MAPPERS;
	}

	private static boolean hasSystemEnvironmentName(PropertySource<?> source) {
		String name = source.getName();
		return StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(name)
				|| name.endsWith("-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
	}

	// 完全可枚举的...
	private static boolean isFullEnumerable(PropertySource<?> source) {
		PropertySource<?> rootSource = getRootSource(source);
		// 如果为 Map ..
		if (rootSource.getSource() instanceof Map) {
			// Check we're not security restricted
			// 检查没有安全限制 ...
			try {
				((Map<?, ?>) rootSource.getSource()).size();
			}
			catch (UnsupportedOperationException ex) {
				return false;
			}
		}
		return (source instanceof EnumerablePropertySource);
	}

	private static PropertySource<?> getRootSource(PropertySource<?> source) {
		while (source.getSource() != null && source.getSource() instanceof PropertySource) {
			source = (PropertySource<?>) source.getSource();
		}
		return source;
	}

}
