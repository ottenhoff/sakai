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

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.ProviderManager;

import org.sakaiproject.login.saml.SakaiSamlAuthenticationConverter;

import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.tool.api.SessionManager;

/**
 * Spring configuration for SAML 2.0 authentication in Sakai.
 * This replaces the older XML-based configuration that used the deprecated spring-security-saml-extension.
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SakaiSamlConfiguration {

    @Autowired
    private UsageSessionService usageSessionService;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Value("${sakai.saml.idp.metadata.path:/opt/tomcat/sakai/ssocircle_idp.xml}")
    private String idpMetadataPath;
    
    @Value("${sakai.saml.idp.metadata.url:}")
    private String idpMetadataUrl;
    
    @Value("${sakai.saml.sp.entityId:SakaiSAMLApp}")
    private String spEntityId;
    
    @Value("${sakai.saml.sp.metadata.path:/opt/tomcat/sakai/SakaiSAMLApp_sp.xml}")
    private String spMetadataPath;
    
    @Value("${sakai.saml.idp.entityId:http://idp.ssocircle.com}")
    private String defaultIdpEntityId;
    
    @Value("${sakai.saml.mock.enabled:false}")
    private boolean mockSamlEnabled;
    
    @Value("${sakai.saml.mock.baseUrl:http://localhost:8080/mocksaml}")
    private String mockSamlBaseUrl;
    
    @Value("${sakai.saml.idp.sso.url:}")
    private String idpSsoUrl;
    
    @Autowired(required = false)
    private SakaiSamlAuthenticationConverter authenticationConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Configure the custom authentication provider with our converter if available
        if (authenticationConverter != null) {
            log.info("Registering SakaiSamlAuthenticationConverter for SAML authentication");
            
            // Create the authentication provider with our converter
            OpenSaml4AuthenticationProvider provider = new OpenSaml4AuthenticationProvider();
            provider.setResponseAuthenticationConverter(authenticationConverter);
            
            // Configure login with the provider
            http.saml2Login(saml2 -> saml2
                .authenticationManager(new ProviderManager(provider)));
        }
        
        http
            .csrf().disable()
            .authorizeRequests(authorize -> authorize
                .anyRequest().authenticated()
            )
            .saml2Login(saml2 -> saml2
                .loginProcessingUrl("/container/saml/SSO")
                .defaultSuccessUrl("/portal", true)
                .failureUrl("/portal/xlogin")
            )
            // Configure SAML 2.0 logout
            .saml2Logout(saml2 -> saml2
                .logoutUrl("/container/saml/logout")
            )
            // Configure general logout behavior
            .logout(logout -> logout
                .logoutSuccessUrl("/portal")
                // Add custom logout handler to clear Sakai sessions
                .addLogoutHandler((request, response, authentication) -> {
                    try {
                        // Clear Sakai session
                        if (sessionManager != null) {
                            sessionManager.getCurrentSession().invalidate();
                        }
                        // Clear usage session data
                        if (usageSessionService != null) {
                            usageSessionService.logout();
                        }
                    } catch (Exception e) {
                        log.error("Error invalidating Sakai session during SAML logout", e);
                    }
                })
            );
        
        return http.build();
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        List<RelyingPartyRegistration> registrations = new ArrayList<>();
        
        try {
            if (mockSamlEnabled) {
                // Configure for MockSaml testing environment
                log.info("Configuring SAML for MockSaml testing environment at {}", mockSamlBaseUrl);
                
                RelyingPartyRegistration.Builder builder = RelyingPartyRegistration.withRegistrationId("sakai-saml-mock")
                    .entityId(spEntityId)
                    .assertionConsumerServiceLocation("{baseUrl}/container/saml/SSO")
                    .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout");
                
                // If metadata URL is provided, use it
                if (idpMetadataUrl != null && !idpMetadataUrl.isEmpty()) {
                    builder = RelyingPartyRegistrations
                        .fromMetadataLocation(idpMetadataUrl)
                        .entityId(spEntityId)
                        .registrationId("sakai-saml-mock")
                        .assertionConsumerServiceLocation("{baseUrl}/container/saml/SSO")
                        .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout");
                } else {
                    // Configure manually for MockSaml
                    builder.assertingPartyDetails(party -> party
                        .entityId(defaultIdpEntityId)
                        .singleSignOnServiceLocation(idpSsoUrl)
                        .wantAuthnRequestsSigned(false)
                    );
                }
                
                registrations.add(builder.build());
                log.info("MockSaml configuration complete");
                
            } else {
                // Standard production configuration
                File idpMetadataFile = new File(idpMetadataPath);
                if (idpMetadataFile.exists()) {
                    RelyingPartyRegistration registration = RelyingPartyRegistrations
                        .fromMetadataLocation(idpMetadataFile.toURI().toString())
                        .entityId(spEntityId)
                        .registrationId("sakai-saml")
                        .assertionConsumerServiceLocation("{baseUrl}/container/saml/SSO")
                        .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout")
                        .build();
                    
                    registrations.add(registration);
                    log.info("Loaded SAML IdP metadata from {}", idpMetadataPath);
                } else {
                    log.warn("IdP metadata file not found at {}", idpMetadataPath);
                    
                    // If metadata URL is provided as fallback, use it
                    if (idpMetadataUrl != null && !idpMetadataUrl.isEmpty()) {
                        try {
                            RelyingPartyRegistration registration = RelyingPartyRegistrations
                                .fromMetadataLocation(idpMetadataUrl)
                                .entityId(spEntityId)
                                .registrationId("sakai-saml")
                                .assertionConsumerServiceLocation("{baseUrl}/container/saml/SSO")
                                .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout")
                                .build();
                                
                            registrations.add(registration);
                            log.info("Loaded SAML IdP metadata from URL: {}", idpMetadataUrl);
                        } catch (Exception e) {
                            log.error("Error loading SAML IdP metadata from URL", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error loading SAML IdP metadata", e);
        }
        
        return new InMemoryRelyingPartyRegistrationRepository(registrations);
    }
}