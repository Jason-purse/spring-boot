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

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.security.ConditionalOnDefaultWebSecurity;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The default configuration for web security. It relies on Spring Security's
 * content-negotiation strategy to determine what sort of authentication to use. If the
 * user specifies their own {@link WebSecurityConfigurerAdapter} or
 * {@link SecurityFilterChain} bean, this will back-off completely and the users should
 * specify all the bits that they want to configure as part of the custom security
 * configuration.
 *
 *
 * web Security的默认配置, 它依赖于Spring Security的内容协商策略去决定(那种类型的认证使用) ..
 * 如果用户指定了它们自己的WebSecurityConfigurerAdapter  或者SecurityFilterChain , 那么这将会避让 并且用户应该指定所有的细节(关于想要配置自定义安全配置的一部分) ...
 * @author Madhura Bhave
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDefaultWebSecurity
@ConditionalOnWebApplication(type = Type.SERVLET)
class SpringBootWebSecurityConfiguration {

	/**
	 * 这里使用优先级的原因就是,相对于注册到servlet的容器的其他过滤器  有一个优先级顺序 ...
	 * @param http
	 * @return
	 * @throws Exception
	 */
	@Bean
	@Order(SecurityProperties.BASIC_AUTH_ORDER)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeRequests().anyRequest().authenticated().and().formLogin().and().httpBasic();
		return http.build();
	}

}
