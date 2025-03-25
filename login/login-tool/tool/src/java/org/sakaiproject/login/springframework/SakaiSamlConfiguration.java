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
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.login.tool.SkinnableLogin;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.ProviderManager;

import org.sakaiproject.login.saml.SakaiSamlAuthenticationConverter;

import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

/**
 * Spring configuration for SAML 2.0 authentication in Sakai.
 * This replaces the older XML-based configuration that used the deprecated spring-security-saml-extension.
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SakaiSamlConfiguration {

    @Autowired(required = false)
    private SakaiSamlAuthenticationConverter authenticationConverter;

    @Autowired
    private ServerConfigurationService serverConfigurationService;

    @Autowired
    private UserDirectoryService userDirectoryService;

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
    
    // Keystore configuration
    @Value("${sakai.saml.keystore.path:}")
    private String keystorePath;
    
    @Value("${sakai.saml.keystore.password:}")
    private String keystorePassword;
    
    @Value("${sakai.saml.keystore.alias:}")
    private String keystoreAlias;
    
    @Value("${sakai.saml.keystore.privatekey.password:}")
    private String privateKeyPassword;

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
                .loginProcessingUrl("/container/saml/{registrationId}/SSO")
                .defaultSuccessUrl(serverConfigurationService.getServerUrl() + "/portal/login", true)
                .failureUrl(serverConfigurationService.getServerUrl() + "/portal/xlogin")
                // Add custom success handler to create Sakai session
                .successHandler((request, response, authentication) -> {
                    try {
                        // Get the authenticated principal from the SAML authentication
                        if (authentication instanceof Saml2Authentication) {
                            Saml2Authentication samlAuth =
                                (Saml2Authentication) authentication;
                            
                            // Get the username from the principal
                            String username = samlAuth.getName();
                            log.info("SAML authentication successful for user: {}", username);
                            
                            // Create a Sakai session for this user
                            final User u = userDirectoryService.getUserByEid(username);
                            log.info("SAML Found user: {}", u);

                            Session session = sessionManager.getCurrentSession();

                            if (usageSessionService.login(
                                    u.getId(), // uid (internal user ID)
                                    u.getEid(), // eid (external user ID)
                                    request.getRemoteAddr(),
                                    request.getHeader("user-agent"),
                                    UsageSessionService.EVENT_LOGIN_CONTAINER)) {
                                
                                log.info("Successfully created Sakai session for user: {}", username);
                                
                                // Redirect to portal or requested URL
                                String url = serverConfigurationService.getPortalUrl() + "/login";
                                String returnUrl = (String) session.getAttribute(Tool.HELPER_DONE_URL);
                                if (returnUrl != null && !returnUrl.isEmpty()) {
                                    url = returnUrl;
                                }
                                
                                // Remove session attributes
                                session.removeAttribute(Tool.HELPER_MESSAGE);
                                session.removeAttribute(Tool.HELPER_DONE_URL);
                                session.setAttribute(SkinnableLogin.ATTR_CONTAINER_SUCCESS, SkinnableLogin.ATTR_CONTAINER_SUCCESS);

                                log.info("SAML send user to: {}", url);
                                response.sendRedirect(response.encodeRedirectURL(url));
                            } else {
                                log.error("Failed to create Sakai session for user: {}", username);
                                response.sendRedirect(response.encodeRedirectURL(serverConfigurationService.getPortalUrl() + "/xlogin?failed=true"));
                            }
                        } else {
                            log.error("Authentication is not a Saml2Authentication: {}", authentication.getClass().getName());
                            response.sendRedirect(response.encodeRedirectURL(serverConfigurationService.getPortalUrl() + "/xlogin?failed=true"));
                        }
                    } catch (Exception e) {
                        log.error("Error in SAML success handler", e);
                        try {
                            response.sendRedirect(response.encodeRedirectURL(serverConfigurationService.getPortalUrl() + "/xlogin?failed=true"));
                        } catch (Exception ex) {
                            log.error("Failed to redirect after error", ex);
                        }
                    }
                })
            )
            // Configure SAML 2.0 logout
            .saml2Logout(saml2 -> saml2
                .logoutUrl("/container/saml/logout")
            )
            // Configure general logout behavior
            .logout(logout -> logout
                .logoutSuccessUrl(serverConfigurationService.getServerUrl() + "/portal")
                // Add custom logout handler to clear Sakai sessions
                .addLogoutHandler((request, response, authentication) -> {
                    try {
                        // Clear Sakai session
                        sessionManager.getCurrentSession().invalidate();
                        // Clear usage session data
                        usageSessionService.logout();
                    } catch (Exception e) {
                        log.error("Error invalidating Sakai session during SAML logout", e);
                    }
                })
            );
        
        return http.build();
    }

    /**
     * Creates SAML signing credentials from configured keystore
     * @return SAML2 signing credential for the SP
     */
    private Saml2X509Credential createSigningCredential() {
        if (keystorePath == null || keystorePath.isEmpty() || 
            keystorePassword == null || keystorePassword.isEmpty() ||
            keystoreAlias == null || keystoreAlias.isEmpty() ||
            privateKeyPassword == null || privateKeyPassword.isEmpty()) {
            log.warn("Keystore configuration incomplete. Cannot create signing credentials.");
            return null;
        }
        
        try {
            log.info("Loading signing credentials from keystore: {}", keystorePath);
            java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePassword.toCharArray());
            }
            
            java.security.PrivateKey pk = (java.security.PrivateKey) ks.getKey(
                keystoreAlias, privateKeyPassword.toCharArray());
            java.security.cert.X509Certificate certificate = 
                (java.security.cert.X509Certificate) ks.getCertificate(keystoreAlias);
            
            Saml2X509Credential credential = Saml2X509Credential.signing(pk, certificate);
            log.info("Successfully loaded signing credentials from keystore");
            return credential;
        } catch (Exception e) {
            log.error("Error creating signing credentials from keystore", e);
            return null;
        }
    }
    
    /**
     * Creates SAML encryption credentials from configured keystore
     * @return SAML2 encryption credential for the SP
     */
    private Saml2X509Credential createEncryptionCredential() {
        if (keystorePath == null || keystorePath.isEmpty() || 
            keystorePassword == null || keystorePassword.isEmpty() ||
            keystoreAlias == null || keystoreAlias.isEmpty() ||
            privateKeyPassword == null || privateKeyPassword.isEmpty()) {
            log.warn("Keystore configuration incomplete. Cannot create encryption credentials.");
            return null;
        }
        
        try {
            log.info("Loading encryption credentials from keystore: {}", keystorePath);
            java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, keystorePassword.toCharArray());
            }
            
            // For decryption, we need both the private key and certificate
            java.security.PrivateKey pk = (java.security.PrivateKey) ks.getKey(
                keystoreAlias, privateKeyPassword.toCharArray());
            java.security.cert.X509Certificate certificate = 
                (java.security.cert.X509Certificate) ks.getCertificate(keystoreAlias);
            
            // Use decryption method instead of encryption
            Saml2X509Credential credential = Saml2X509Credential.decryption(pk, certificate);
            log.info("Successfully loaded decryption credentials from keystore");
            return credential;
        } catch (Exception e) {
            log.error("Error creating decryption credentials from keystore", e);
            return null;
        }
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        List<RelyingPartyRegistration> registrations = new ArrayList<>();
        boolean registrationCreated = false;
        
        try {
            // Load credentials from keystore if configured
            Saml2X509Credential signingCredential = createSigningCredential();
            Saml2X509Credential encryptionCredential = createEncryptionCredential();
            
            if (mockSamlEnabled) {
                // Configure for MockSaml testing environment
                log.info("Configuring SAML for MockSaml testing environment at {}", mockSamlBaseUrl);
                
                RelyingPartyRegistration.Builder builder = RelyingPartyRegistration.withRegistrationId("sakai-saml-mock")
                    .entityId(spEntityId)
                    .assertionConsumerServiceLocation("{baseUrl}/container/saml/{registrationId}/SSO")
                    .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout");
                
                // Add signing/encryption credentials if available
                if (signingCredential != null) {
                    builder.signingX509Credentials(c -> c.add(signingCredential));
                }
                if (encryptionCredential != null) {
                    builder.decryptionX509Credentials(c -> c.add(encryptionCredential));
                }
                
                // If metadata URL is provided, use it to get IdP settings
                if (idpMetadataUrl != null && !idpMetadataUrl.isEmpty()) {
                    log.info("Loading IdP configuration from metadata URL: {}", idpMetadataUrl);
                    
                    // This loads IdP-specific details from metadata (entityId, SSO URL, certificates)
                    // but we still need to configure our SP settings on top of it
                    builder = RelyingPartyRegistrations
                        .fromMetadataLocation(idpMetadataUrl)
                        // These are our SP settings, not from the IdP metadata:
                        .entityId(spEntityId)  // Our SP's entityId
                        .registrationId("sakai-saml-mock")  // Internal identifier
                        .assertionConsumerServiceLocation("{baseUrl}/container/saml/{registrationId}/SSO")  // Where to receive SAML responses
                        .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout");  // Where to receive logout responses
                    
                    // Add our SP's signing/encryption credentials if available
                    if (signingCredential != null) {
                        builder.signingX509Credentials(c -> c.add(signingCredential));
                    }
                    if (encryptionCredential != null) {
                        builder.decryptionX509Credentials(c -> c.add(encryptionCredential));
                    }
                } else {
                    // Configure manually for MockSaml
                    builder.assertingPartyDetails(party -> party
                        .entityId(defaultIdpEntityId)
                        .singleSignOnServiceLocation(idpSsoUrl)
                        .wantAuthnRequestsSigned(false)
                    );
                }
                
                registrations.add(builder.build());
                registrationCreated = true;
                log.info("MockSaml configuration complete");
                
            } else {
                // Standard production configuration
                File idpMetadataFile = new File(idpMetadataPath);
                if (idpMetadataFile.exists()) {
                    log.info("Loading IdP configuration from metadata file: {}", idpMetadataPath);
                    
                    // This loads IdP-specific details from metadata file (entityId, SSO URL, certificates)
                    // but we still need to configure our SP settings on top of it
                    RelyingPartyRegistration.Builder builder = RelyingPartyRegistrations
                        .fromMetadataLocation(idpMetadataFile.toURI().toString())
                        // These are our SP settings, not from the IdP metadata:
                        .entityId(spEntityId)  // Our SP's entityId
                        .registrationId("sakai-saml")  // Internal identifier
                        .assertionConsumerServiceLocation("{baseUrl}/container/saml/{registrationId}/SSO")  // Where to receive SAML responses
                        .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout");  // Where to receive logout responses
                    
                    // Add our SP's signing/encryption credentials if available
                    if (signingCredential != null) {
                        builder.signingX509Credentials(c -> c.add(signingCredential));
                    }
                    if (encryptionCredential != null) {
                        builder.decryptionX509Credentials(c -> c.add(encryptionCredential));
                    }
                    
                    registrations.add(builder.build());
                    registrationCreated = true;
                    log.info("Successfully configured SAML with IdP metadata from file: {}", idpMetadataPath);
                } else {
                    log.warn("IdP metadata file not found at {}", idpMetadataPath);
                    
                    // If metadata URL is provided as fallback, use it to get IdP settings
                    if (idpMetadataUrl != null && !idpMetadataUrl.isEmpty()) {
                        try {
                            log.info("Loading IdP configuration from metadata URL (fallback): {}", idpMetadataUrl);
                            
                            // This loads IdP-specific details from metadata (entityId, SSO URL, certificates)
                            // but we still need to configure our SP settings on top of it
                            RelyingPartyRegistration.Builder builder = RelyingPartyRegistrations
                                .fromMetadataLocation(idpMetadataUrl)
                                // These are our SP settings, not from the IdP metadata:
                                .entityId(spEntityId)  // Our SP's entityId
                                .registrationId("sakai-saml")  // Internal identifier
                                .assertionConsumerServiceLocation("{baseUrl}/container/saml/{registrationId}/SSO")  // Where to receive SAML responses
                                .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout");  // Where to receive logout responses
                            
                            // Add our SP's signing/encryption credentials if available
                            if (signingCredential != null) {
                                builder.signingX509Credentials(c -> c.add(signingCredential));
                            }
                            if (encryptionCredential != null) {
                                builder.decryptionX509Credentials(c -> c.add(encryptionCredential));
                            }
                            
                            registrations.add(builder.build());
                            registrationCreated = true;
                            log.info("Successfully configured SAML with IdP metadata from URL: {}", idpMetadataUrl);
                        } catch (Exception e) {
                            log.error("Error loading SAML IdP metadata from URL", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error loading SAML IdP metadata", e);
        }
        
        // If no registrations were created, add a default fallback registration
        // This prevents the "registrations cannot be empty" error
        if (!registrationCreated) {
            log.warn("No valid SAML configurations found, creating a default fallback registration");
            
            // Create a minimal fallback configuration
            RelyingPartyRegistration fallback = RelyingPartyRegistration
                .withRegistrationId("sakai-saml-fallback")
                .entityId(spEntityId)
                .assertionConsumerServiceLocation("{baseUrl}/container/saml/{registrationId}/SSO")
                .singleLogoutServiceLocation("{baseUrl}/container/saml/SingleLogout")
                .assertingPartyDetails(party -> party
                    .entityId("http://dummy-idp-fallback")
                    .singleSignOnServiceLocation("http://dummy-idp-fallback/SSO")
                    .wantAuthnRequestsSigned(false)
                )
                .build();
            
            registrations.add(fallback);
        }
        
        return new InMemoryRelyingPartyRegistrationRepository(registrations);
    }
}