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
 * Properties are loaded from the following locations (in order of precedence, later overrides earlier):
 * 1. Classpath: /sakai-saml.properties
 * 2. Sakai home: ${sakai.home}/sakai-saml.properties
 * 3. Tomcat conf: ${catalina.base}/sakai/saml/sakai-saml.properties (or CATALINA_HOME if base not set)
 * 4. Environment-specific properties from all the above locations
 * 
 * Environment-specific configurations can be activated by setting the saml.env system property:
 * - For production: no setting needed (default), or saml.env=production
 * - For other environments: saml.env=custom (loads sakai-saml-custom.properties)
 */
@Configuration
// Default properties from classpath and sakai.home
@PropertySource(value = "classpath:sakai-saml.properties", ignoreResourceNotFound = true)
@PropertySource(value = "file:${sakai.home}/sakai-saml.properties", ignoreResourceNotFound = true)

// Tomcat-specific properties (highest precedence for default properties)
@PropertySource(value = "file:${catalina.base:${catalina.home}}/sakai/saml/sakai-saml.properties", ignoreResourceNotFound = true)

// Environment-specific properties from classpath and sakai.home
@PropertySource(value = "classpath:sakai-saml-${saml.env:production}.properties", ignoreResourceNotFound = true)
@PropertySource(value = "file:${sakai.home}/sakai-saml-${saml.env:production}.properties", ignoreResourceNotFound = true)

// Environment-specific properties from Tomcat directory (highest precedence)
@PropertySource(value = "file:${catalina.base:${catalina.home}}/sakai/saml/sakai-saml-${saml.env:production}.properties", ignoreResourceNotFound = true)

@Slf4j
public class SakaiSamlPropertiesConfiguration {

    @Autowired
    private Environment environment;
    
    @Bean
    public String samlEnvironment() {
        String env = environment.getProperty("saml.env", "production");
        log.info("SAML environment configured as: {}", env);
        
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
