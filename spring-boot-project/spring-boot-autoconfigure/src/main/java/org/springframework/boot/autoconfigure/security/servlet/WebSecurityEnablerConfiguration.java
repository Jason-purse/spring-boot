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

package org.springframework.boot.autoconfigure.security.servlet;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Adds the{@link EnableWebSecurity @EnableWebSecurity} annotation if Spring Security is
 * on the classpath. This will make sure that the annotation is present with default
 * security auto-configuration and also if the user adds custom security and forgets to
 * add the annotation. If {@link EnableWebSecurity @EnableWebSecurity} has already been
 * added or if a bean with name {@value BeanIds#SPRING_SECURITY_FILTER_CHAIN} has been
 * configured by the user, this will back-off.
 *
 * 首先增加这个注解(如果spring security在类路径上) , 这将确保注解与默认的security 自动配置一同存在并且如果用户增加了自定义security 并且(在忘记增加此注解的时候会启用) ..
 * 如果此注解已经被增加或者 一个具有 {@value BeanIds#SPRING_SECURITY_FILTER_CHAIN}的 bean 已经被用户配置,那么这将会失效(也就是不会配置)  ...
 *
 * 同样可以看出来,同样是启用默认的 配置,例如 @EnableWebSecurity
 * 这也看出来,@Bean的注册在于@Configuration 解析之后 ...
 * @author Madhura Bhave
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(name = BeanIds.SPRING_SECURITY_FILTER_CHAIN)
@ConditionalOnClass(EnableWebSecurity.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
class WebSecurityEnablerConfiguration {

}
