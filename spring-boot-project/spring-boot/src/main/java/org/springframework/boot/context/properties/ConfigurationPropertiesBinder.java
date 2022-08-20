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

package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.AbstractBindHandler;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Bindable.BindRestriction;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BoundPropertiesTrackingBindHandler;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.handler.IgnoreErrorsBindHandler;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.UnboundElementsSourceFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.PropertySources;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * Internal class used by the {@link ConfigurationPropertiesBindingPostProcessor} to
 * handle the actual {@link ConfigurationProperties @ConfigurationProperties} binding.
 *
 * 后置处理器内部使用的类(被用来处理 @ConfigurationProperties 绑定) ...
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 */
class ConfigurationPropertiesBinder {

	private static final String BEAN_NAME = "org.springframework.boot.context.internalConfigurationPropertiesBinder";

	private static final String FACTORY_BEAN_NAME = "org.springframework.boot.context.internalConfigurationPropertiesBinderFactory";

	private static final String VALIDATOR_BEAN_NAME = EnableConfigurationProperties.VALIDATOR_BEAN_NAME;

	private final ApplicationContext applicationContext;

	private final PropertySources propertySources;

	private final Validator configurationPropertiesValidator;

	private final boolean jsr303Present;

	private volatile Validator jsr303Validator;

	private volatile Binder binder;

	ConfigurationPropertiesBinder(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;

		this.propertySources = new PropertySourcesDeducer(applicationContext).getPropertySources();

		// 配置属性验证器
		this.configurationPropertiesValidator = getConfigurationPropertiesValidator(applicationContext);
		// jsr 303 是否出现 ...
		this.jsr303Present = ConfigurationPropertiesJsr303Validator.isJsr303Present(applicationContext);
	}

	// 配置属性绑定器进行绑定 ...
	BindResult<?> bind(ConfigurationPropertiesBean propertiesBean) {
		// 获取绑定目标 ..
		Bindable<?> target = propertiesBean.asBindTarget();
		ConfigurationProperties annotation = propertiesBean.getAnnotation();
		// 根据注解拿取绑定处理器 ..
		// 通过装饰器模式 返回了一个bindHandler ...
		BindHandler bindHandler = getBindHandler(target, annotation);
		//
		return getBinder().bind(annotation.prefix(), target, bindHandler);
	}

	Object bindOrCreate(ConfigurationPropertiesBean propertiesBean) {
		Bindable<?> target = propertiesBean.asBindTarget();
		ConfigurationProperties annotation = propertiesBean.getAnnotation();
		BindHandler bindHandler = getBindHandler(target, annotation);
		return getBinder().bindOrCreate(annotation.prefix(), target, bindHandler);
	}

	private Validator getConfigurationPropertiesValidator(ApplicationContext applicationContext) {
		if (applicationContext.containsBean(VALIDATOR_BEAN_NAME)) {
			return applicationContext.getBean(VALIDATOR_BEAN_NAME, Validator.class);
		}
		return null;
	}


	// 获取绑定处理器 ..
	private <T> BindHandler getBindHandler(Bindable<T> target, ConfigurationProperties annotation) {

		List<Validator> validators = getValidators(target);

		BindHandler handler = getHandler();
		handler = new ConfigurationPropertiesBindHandler(handler);

		// 这里使用到了装饰器模式 ...
		if (annotation.ignoreInvalidFields()) {
			// 如果忽略无效属性 ...
			handler = new IgnoreErrorsBindHandler(handler);
		}

		// 如果不忽略 ...
		if (!annotation.ignoreUnknownFields()) {
			UnboundElementsSourceFilter filter = new UnboundElementsSourceFilter();
			handler = new NoUnboundElementsBindHandler(handler, filter);
		}
		if (!validators.isEmpty()) {
			// 给上验证绑定处理器
			handler = new ValidationBindHandler(handler, validators.toArray(new Validator[0]));
		}

		for (ConfigurationPropertiesBindHandlerAdvisor advisor : getBindHandlerAdvisors()) {
			handler = advisor.apply(handler);
		}
		return handler;
	}

	private IgnoreTopLevelConverterNotFoundBindHandler getHandler() {
		// 获取Bound ... properties ...
		BoundConfigurationProperties bound = BoundConfigurationProperties.get(this.applicationContext);

		return (bound != null)
				// 如果没有转换器忽略异常,但是  绑定过程交给了代理 ...
				? new IgnoreTopLevelConverterNotFoundBindHandler(new BoundPropertiesTrackingBindHandler(bound::add))
				// 表示没有配置 ...
				: new IgnoreTopLevelConverterNotFoundBindHandler();
	}

	// 可绑定对象中获取 验证器 ...
	private List<Validator> getValidators(Bindable<?> target) {

		List<Validator> validators = new ArrayList<>(3);
		if (this.configurationPropertiesValidator != null) {
			validators.add(this.configurationPropertiesValidator);
		}
		// jsr303 并且如果它有Validated 注解 ..
		if (this.jsr303Present && target.getAnnotation(Validated.class) != null) {
			validators.add(getJsr303Validator());
		}
		// 如果它本身是一个校验器 ??? 也不是不行 ...
		if (target.getValue() != null && target.getValue().get() instanceof Validator) {
			validators.add((Validator) target.getValue().get());
		}
		return validators;
	}

	private Validator getJsr303Validator() {
		if (this.jsr303Validator == null) {
			// 直接使用 ConfigurationPropertiesJsr303Validator
			this.jsr303Validator = new ConfigurationPropertiesJsr303Validator(this.applicationContext);
		}
		return this.jsr303Validator;
	}

	private List<ConfigurationPropertiesBindHandlerAdvisor> getBindHandlerAdvisors() {
		// 还可以有 advisor(顾问) ...??
		return this.applicationContext.getBeanProvider(ConfigurationPropertiesBindHandlerAdvisor.class).orderedStream()
				.collect(Collectors.toList());
	}

	// 获取binder ..
	private Binder getBinder() {

		if (this.binder == null) {
			// 重新new 一个 Binder ...
			this.binder = new Binder(getConfigurationPropertySources(), getPropertySourcesPlaceholdersResolver(),
					getConversionServices(), getPropertyEditorInitializer(), null,
					ConfigurationPropertiesBindConstructorProvider.INSTANCE);
		}
		return this.binder;
	}

	private Iterable<ConfigurationPropertySource> getConfigurationPropertySources() {
		return ConfigurationPropertySources.from(this.propertySources);
	}

	private PropertySourcesPlaceholdersResolver getPropertySourcesPlaceholdersResolver() {
		return new PropertySourcesPlaceholdersResolver(this.propertySources);
	}

	private List<ConversionService> getConversionServices() {
		return new ConversionServiceDeducer(this.applicationContext).getConversionServices();
	}

	private Consumer<PropertyEditorRegistry> getPropertyEditorInitializer() {
		if (this.applicationContext instanceof ConfigurableApplicationContext) {
			return ((ConfigurableApplicationContext) this.applicationContext).getBeanFactory()::copyRegisteredEditorsTo;
		}
		return null;
	}

	static void register(BeanDefinitionRegistry registry) {
		// 工厂
		if (!registry.containsBeanDefinition(FACTORY_BEAN_NAME)) {
			BeanDefinition definition = BeanDefinitionBuilder
					.rootBeanDefinition(ConfigurationPropertiesBinder.Factory.class).getBeanDefinition();
			definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			registry.registerBeanDefinition(ConfigurationPropertiesBinder.FACTORY_BEAN_NAME, definition);
		}
		// bean ..
		// 然后它就创建出 ConfigurationPropertiesBinder
		if (!registry.containsBeanDefinition(BEAN_NAME)) {
			BeanDefinition definition = BeanDefinitionBuilder
					.rootBeanDefinition(ConfigurationPropertiesBinder.class,
							() -> ((BeanFactory) registry)
									.getBean(FACTORY_BEAN_NAME, ConfigurationPropertiesBinder.Factory.class).create())
					.getBeanDefinition();
			definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			registry.registerBeanDefinition(ConfigurationPropertiesBinder.BEAN_NAME, definition);
		}
	}

	static ConfigurationPropertiesBinder get(BeanFactory beanFactory) {
		return beanFactory.getBean(BEAN_NAME, ConfigurationPropertiesBinder.class);
	}

	/**
	 * Factory bean used to create the {@link ConfigurationPropertiesBinder}. The bean
	 * needs to be {@link ApplicationContextAware} since we can't directly inject an
	 * {@link ApplicationContext} into the constructor without causing eager
	 * {@link FactoryBean} initialization.
	 */
	static class Factory implements ApplicationContextAware {

		private ApplicationContext applicationContext;

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.applicationContext = applicationContext;
		}

		ConfigurationPropertiesBinder create() {
			return new ConfigurationPropertiesBinder(this.applicationContext);
		}

	}

	/**
	 * {@link BindHandler} to deal with
	 * {@link ConfigurationProperties @ConfigurationProperties} concerns.
	 *
	 * 用BinderHandler 处理 ConfigurationProperties ..
	 */
	private static class ConfigurationPropertiesBindHandler extends AbstractBindHandler {

		ConfigurationPropertiesBindHandler(BindHandler handler) {
			super(handler);
		}

		@Override
		public <T> Bindable<T> onStart(ConfigurationPropertyName name, Bindable<T> target, BindContext context) {
			return isConfigurationProperties(target.getType().resolve())
					// 给定绑定约束(非直接属性也可以绑定) ..
					? target.withBindRestrictions(BindRestriction.NO_DIRECT_PROPERTY) : target;
		}

		private boolean isConfigurationProperties(Class<?> target) {
			return target != null && MergedAnnotations.from(target).isPresent(ConfigurationProperties.class);
		}

	}

}
