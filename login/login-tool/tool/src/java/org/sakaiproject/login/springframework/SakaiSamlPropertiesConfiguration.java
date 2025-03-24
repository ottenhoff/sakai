/**
 * Copyright (c) 2003-2025 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.login.springframework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for loading SAML properties from the sakai-saml.properties file.
 * This makes property values available for @Value annotations in other configuration classes.
 * 
 * Environment-specific configurations can be activated by setting the sakai.saml.env system property:
 * - For production: no setting needed (default), or sakai.saml.env=production
 * - For testing with MockSaml: sakai.saml.env=mocksaml
 * - For other environments: sakai.saml.env=custom (loads sakai-saml-custom.properties)
 */
@Configuration
@PropertySource(value = "classpath:sakai-saml.properties", ignoreResourceNotFound = true)
@PropertySource(value = "file:${sakai.home}/sakai-saml.properties", ignoreResourceNotFound = true)
@PropertySource(value = "classpath:sakai-saml-${sakai.saml.env:production}.properties", ignoreResourceNotFound = true)
@PropertySource(value = "file:${sakai.home}/sakai-saml-${sakai.saml.env:production}.properties", ignoreResourceNotFound = true)
@Slf4j
public class SakaiSamlPropertiesConfiguration {

    @Autowired
    private Environment environment;
    
    @Bean
    public String samlEnvironment() {
        String env = environment.getProperty("sakai.saml.env", "production");
        log.info("SAML environment configured as: {}", env);
        
        if ("mocksaml".equals(env)) {
            log.info("MockSaml testing environment activated");
        }
        
        return env;
    }

    /**
     * Required for @PropertySource to work with @Value annotations
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}