/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.beans.factory.annotation.DubboConfigBindingBeanPostProcessor;
import org.apache.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer.BEAN_NAME;
import static org.apache.dubbo.config.spring.util.BeanRegistrar.registerInfrastructureBean;
import static org.apache.dubbo.config.spring.util.PropertySourcesUtils.getSubProperties;
import static org.apache.dubbo.config.spring.util.PropertySourcesUtils.normalizePrefix;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.beans.factory.support.BeanDefinitionReaderUtils.registerWithGeneratedName;

/**
 * {@link AbstractConfig Dubbo Config} binding Bean registrar
 *
 * @see EnableDubboConfigBinding
 * @see DubboConfigBindingBeanPostProcessor
 * @since 2.5.8
 */
public class DubboConfigBindingRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private final Log log = LogFactory.getLog(getClass());

    private ConfigurableEnvironment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConfigBinding.class.getName()));

        registerBeanDefinitions(attributes, registry);

    }

    protected void registerBeanDefinitions(AnnotationAttributes attributes, BeanDefinitionRegistry registry) {
        //@EnableDubboConfigBinding(prefix = "dubbo.application", type = ApplicationConfig.class)
        //获取前缀
        String prefix = environment.resolvePlaceholders(attributes.getString("prefix"));
        //获取配置Clas 即要将指定的前缀配置分装成指定的Class对象
        Class<? extends AbstractConfig> configClass = attributes.getClass("type");

        boolean multiple = attributes.getBoolean("multiple");

        registerDubboConfigBeans(prefix, configClass, multiple, registry);

    }

    private void registerDubboConfigBeans(String prefix,
                                          Class<? extends AbstractConfig> configClass,
                                          boolean multiple,
                                          BeanDefinitionRegistry registry) {
        /**
         *从properties文件中根据前缀拿到对应的配置项 比如根据dubbo.application前缀
         * 可以拿到
         *      dubbo.application.name=dubbo-demo-annotation-provider
         *      dubbo.application.logger=log4j
         */
        Map<String, Object> properties = getSubProperties(environment.getPropertySources(), prefix);
        //如果没有相关的配置项 则不需要注册beanDefinition
        if (CollectionUtils.isEmpty(properties)) {
            if (log.isDebugEnabled()) {
                log.debug("There is no property for binding to dubbo config class [" + configClass.getName()
                        + "] within prefix [" + prefix + "]");
            }
            return;
        }
        /**
         * 根据配置项相差beanNames 为啥会有多个
         * 普通的情况 一个dubbo.application前缀对应一个ApplicationConfig类型的Bean
         * 特殊情况 比如dubbo.protocols对应了多个
         * dubbo.protocols.p1.name=dubbo
         * dubbo.protocols.p1.port=20880
         * dubbo.protocols.p2.name=http
         * dubbo.protocols.p2.port=8082
         * 那么就需要对应两个ProtocoConfig类型的Bean 那么就需要两个beanName :p1和p2
         *
         * 这里就是multiple为true和false的区别 名字的区别 根据multiple用来判断是否从配置项中获取beanName
         * 如果multiple 为true 则返回集合中含有多个名称
         * 如果multiple为false 则看看有没有配置id属性 如果没有匹配则自动生成一个beanName
         */
        Set<String> beanNames = multiple ? resolveMultipleBeanNames(properties) :
                Collections.singleton(resolveSingleBeanName(properties, configClass, registry));

        for (String beanName : beanNames) {
            //为每个beanName 注册一个空的BeanDefinition
            registerDubboConfigBean(beanName, configClass, registry);
            //为每个bean注册一个DubboConfigBindingBeanPostProcessor 的bean的后置处理器
            registerDubboConfigBindingBeanPostProcessor(prefix, beanName, multiple, registry);

        }
        //注册一个NamePropertyDefaultValueDubboConfigBeanCustomizer的bean
        //用来把某个xxConfig所对应的beanName设置到name属性中去
        registerDubboConfigBeanCustomizers(registry);

    }

    private void registerDubboConfigBean(String beanName, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {

        BeanDefinitionBuilder builder = rootBeanDefinition(configClass);

        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
        //Spring容器中注册BeanDefinition ApplicationConfig对象
        registry.registerBeanDefinition(beanName, beanDefinition);

        if (log.isInfoEnabled()) {
            log.info("The dubbo config bean definition [name : " + beanName + ", class : " + configClass.getName() +
                    "] has been registered.");
        }

    }

    /**
     * 注册对应前缀的beanPostProcessor
     * @param prefix
     * @param beanName
     * @param multiple
     * @param registry
     */
    private void registerDubboConfigBindingBeanPostProcessor(String prefix, String beanName, boolean multiple,
                                                             BeanDefinitionRegistry registry) {

        Class<?> processorClass = DubboConfigBindingBeanPostProcessor.class;

        BeanDefinitionBuilder builder = rootBeanDefinition(processorClass);
        //真实的前缀 比如 dubbo.registries.r2
        String actualPrefix = multiple ? normalizePrefix(prefix) + beanName : prefix;
        //添加两个构造方法参数值 所以会调用DubboConfigBindingBeanPostProcessor的两个参数的构造方法
        builder.addConstructorArgValue(actualPrefix).addConstructorArgValue(beanName);

        AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

        registerWithGeneratedName(beanDefinition, registry);

        if (log.isInfoEnabled()) {
            log.info("The BeanPostProcessor bean definition [" + processorClass.getName()
                    + "] for dubbo config bean [name : " + beanName + "] has been registered.");
        }

    }

    private void registerDubboConfigBeanCustomizers(BeanDefinitionRegistry registry) {
        registerInfrastructureBean(registry, BEAN_NAME, NamePropertyDefaultValueDubboConfigBeanCustomizer.class);
    }

    @Override
    public void setEnvironment(Environment environment) {

        Assert.isInstanceOf(ConfigurableEnvironment.class, environment);

        this.environment = (ConfigurableEnvironment) environment;

    }

    /**
     *  多个Bean的名称解析
     *    dubbo.protocols.p1.name=dubbo
     *    dubbo.protocols.p1.port=20880
     *    dubbo.protocols.p2.name=http
     *    dubbo.protocols.p2.port=8082
     *
     *    properties中此时的数据是被去掉前缀了：
     *         p1.name=dubbo
     *         p1.port=20880
     *         p2.name=http
     *         p2.port=8082
     *
     */
    private Set<String> resolveMultipleBeanNames(Map<String, Object> properties) {

        Set<String> beanNames = new LinkedHashSet<String>();

        for (String propertyName : properties.keySet()) {

            int index = propertyName.indexOf(".");

            if (index > 0) {
                //截取beanName为 p1 p2
                String beanName = propertyName.substring(0, index);

                beanNames.add(beanName);
            }

        }

        return beanNames;

    }

    /**
     * 解析单个bean的名称
     * @param properties
     * @param configClass
     * @param registry
     * @return
     */
    private String resolveSingleBeanName(Map<String, Object> properties, Class<? extends AbstractConfig> configClass,
                                         BeanDefinitionRegistry registry) {
        //获取id
        String beanName = (String) properties.get("id");

        if (!StringUtils.hasText(beanName)) {

            BeanDefinitionBuilder builder = rootBeanDefinition(configClass);
            //生成beanName
            beanName = BeanDefinitionReaderUtils.generateBeanName(builder.getRawBeanDefinition(), registry);
        }

        return beanName;

    }

}