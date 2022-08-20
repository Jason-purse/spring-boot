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

package org.springframework.boot.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * {@link Conditional @Conditional} that only matches when the specified classes are on
 * the classpath.
 * <p>
 * A {@link #value()} can be safely specified on {@code @Configuration} classes as the
 * annotation metadata is parsed by using ASM before the class is loaded. Extra care is
 * required when placed on {@code @Bean} methods, consider isolating the condition in a
 * separate {@code Configuration} class, in particular if the return type of the method
 * matches the {@link #value target of the condition}.
 *
 * 在Value上指定的类 作为配置类的注解元数据 能够通过ASM 在类加载之前解析 ...(配置类解析过程中,会通过ASM 读取配置类中的@Bean方法) ...
 * 非常小心的是 当放置在@Bean方法上的时候,需要隔离condition在在独立的Configuration 类上,特别是这个方法的返回类型匹配 condition的目标 ...
 *
 * @author Phillip Webb
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnClassCondition.class)
public @interface ConditionalOnClass {

	/**
	 * The classes that must be present. Since this annotation is parsed by loading class
	 * bytecode, it is safe to specify classes here that may ultimately not be on the
	 * classpath, only if this annotation is directly on the affected component and
	 * <b>not</b> if this annotation is used as a composed, meta-annotation. In order to
	 * use this annotation as a meta-annotation, only use the {@link #name} attribute.
	 * @return the classes that must be present
	 *
	 *
	 * 类必须出现在类路径上,因此这些注解必须通过加载类字节码解析 , 在这里指定类是安全的(也许它最终不在类路径上) ,
	 * 并且此注解应该直接出现在组件上(才有效) ,如果作为组合的,元注解是不会生效的 ..
	 * 为了将这个注解使用为元注解,需要使用 name属性 ...
	 */
	Class<?>[] value() default {};

	/**
	 * The classes names that must be present.
	 * @return the class names that must be present.
	 *
	 */
	String[] name() default {};

}
