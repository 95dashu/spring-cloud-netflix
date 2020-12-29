/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.netflix.ribbon;

import java.lang.reflect.Constructor;

import com.netflix.client.IClient;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

import org.springframework.beans.BeanUtils;
import org.springframework.cloud.context.named.NamedContextFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 创建客户端，负载平衡器和客户端配置实例的工厂。它为每个客户端名称创建一个Spring ApplicationContext，并从那里提取所需的bean。
 *
 * A factory that creates client, load balancer and client configuration instances. It
 * creates a Spring ApplicationContext per client name, and extracts the beans that it
 * needs from there.
 *
 * @author Spencer Gibb
 * @author Dave Syer
 */
public class SpringClientFactory extends NamedContextFactory<RibbonClientSpecification> {

	static final String NAMESPACE = "ribbon";

	public SpringClientFactory() {
		super(RibbonClientConfiguration.class, NAMESPACE, "ribbon.client.name");
	}

	/**
	 * Get the rest client associated with the name.
	 * @param name name to search by
	 * @param clientClass the class of the client bean
	 * @param <C> {@link IClient} subtype
	 * @return {@link IClient} instance
	 * @throws RuntimeException if any error occurs
	 */
	public <C extends IClient<?, ?>> C getClient(String name, Class<C> clientClass) {
		return getInstance(name, clientClass);
	}

	/**
	 * Get the load balancer associated with the name.
	 * @param name name to search by
	 * @return {@link ILoadBalancer} instance
	 * @throws RuntimeException if any error occurs
	 */
	public ILoadBalancer getLoadBalancer(String name) {
		return getInstance(name, ILoadBalancer.class);
	}

	/**
	 * Get the client config associated with the name.
	 * @param name name to search by
	 * @return {@link IClientConfig} instance
	 * @throws RuntimeException if any error occurs
	 */
	public IClientConfig getClientConfig(String name) {
		return getInstance(name, IClientConfig.class);
	}

	/**
	 * Get the load balancer context associated with the name.
	 * @param serviceId id of the service to search by
	 * @return {@link RibbonLoadBalancerContext} instance
	 * @throws RuntimeException if any error occurs
	 */
	public RibbonLoadBalancerContext getLoadBalancerContext(String serviceId) {
		return getInstance(serviceId, RibbonLoadBalancerContext.class);
	}

	static <C> C instantiateWithConfig(Class<C> clazz, IClientConfig config) {
		return instantiateWithConfig(null, clazz, config);
	}

	/**
	 * 初始化一个 Client，根据 config
	 *
	 * @param context
	 * @param clazz
	 * @param config
	 * @param <C>
	 * @return
	 */
	static <C> C instantiateWithConfig(AnnotationConfigApplicationContext context, Class<C> clazz, IClientConfig config) {
		// <1> 根据构造器，实例化 client 对象
		C result = null;
		try {
			Constructor<C> constructor = clazz.getConstructor(IClientConfig.class);
			result = constructor.newInstance(config);
		}
		catch (Throwable e) {
			// Ignored
		}

		// <2> 根据 class 实例化对象
		if (result == null) {
			// 调用 spring 的实例化方式
			result = BeanUtils.instantiateClass(clazz);
			if (result instanceof IClientConfigAware) {
				((IClientConfigAware) result).initWithNiwsConfig(config);
			}
			if (context != null) {
				// <3> 如果 context 不为空，就将实例化的这个对象，放入容器中
				// autowireBean：会自动创建一个 RootBeanDefinition 对象
				context.getAutowireCapableBeanFactory().autowireBean(result);
			}
		}
		return result;
	}

	@Override
	public <C> C getInstance(String name, Class<C> type) {
		// <1> 从 application context 中获取，存在就直接返回了
		C instance = super.getInstance(name, type);
		if (instance != null) {
			return instance;
		}
		// <2> 配置文件实例化对象
		IClientConfig config = getInstance(name, IClientConfig.class);
		return instantiateWithConfig(getContext(name), type, config);
	}

	@Override
	protected AnnotationConfigApplicationContext getContext(String name) {
		return super.getContext(name);
	}

}
