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
package org.sakaiproject.login.saml;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider.ResponseToken;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;

/**
 * Custom SAML 2.0 authentication converter for Sakai that extracts usernames
 * from eduPersonPrincipalName (EPPN) or UserPrincipalName (UPN) attributes.
 * This replaces the functionality in the old EppnSamlFilter and UpnSamlFilter classes.
 */
@Slf4j
public class SakaiSamlAuthenticationConverter implements Consumer<OpenSaml4AuthenticationProvider.ResponseToken> {

    @Setter private String eppnAttributeName = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";
    @Setter private String upnAttributeName = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn";
    @Setter private String defaultAttributeName = "sub";
    @Setter private boolean appendAtSign = false;
    @Setter private String atSignDomain = "";
    
    /**
     * Extract the appropriate username value from the SAML attributes
     * with priority for EPPN, then UPN, then subject.
     *
     * @param attributes The SAML attributes from the assertion
     * @return The username to use for authentication
     */
    protected String extractUsername(Map<String, List<Object>> attributes) {
        // Try EPPN first
        if (attributes.containsKey(eppnAttributeName) && !attributes.get(eppnAttributeName).isEmpty()) {
            String eppn = attributes.get(eppnAttributeName).get(0).toString();
            log.debug("Found EPPN attribute: {}", eppn);
            return eppn;
        }
        
        // Try UPN next
        if (attributes.containsKey(upnAttributeName) && !attributes.get(upnAttributeName).isEmpty()) {
            String upn = attributes.get(upnAttributeName).get(0).toString();
            log.debug("Found UPN attribute: {}", upn);
            return upn;
        }
        
        // Fall back to the subject or default attribute
        if (attributes.containsKey(defaultAttributeName) && !attributes.get(defaultAttributeName).isEmpty()) {
            String username = attributes.get(defaultAttributeName).get(0).toString();
            log.debug("Using default attribute: {}", username);
            
            // Optionally append domain for simple usernames
            if (appendAtSign && !username.contains("@") && !atSignDomain.isEmpty()) {
                username = username + "@" + atSignDomain;
                log.debug("Appended domain to username: {}", username);
            }
            
            return username;
        }
        
        log.error("Could not find a suitable username attribute in the SAML assertion");
        return null;
    }
    
    @Override
    public void accept(ResponseToken responseToken) {
        Saml2AuthenticationToken token = responseToken.getAuthenticationToken();
        RelyingPartyRegistration registration = token.getRelyingPartyRegistration();
        
        log.debug("Processing SAML response for registration: {}", registration.getRegistrationId());
        
        // Get the response attributes
        Map<String, List<Object>> attributes = responseToken.getResponse().getAttributes();
        
        // Extract username
        String username = extractUsername(attributes);
        if (username == null) {
            log.error("No username could be extracted from SAML assertion");
            return;
        }
        
        // Create authenticated principal with the attributes
        DefaultSaml2AuthenticatedPrincipal principal = new DefaultSaml2AuthenticatedPrincipal(username, attributes);
        
        // Set name ID format and value
        principal.setNameIdFormat(responseToken.getResponse().getNameIdFormat());
        principal.setNameId(responseToken.getResponse().getNameId());
        
        // Create the Saml2Authentication with appropriate authorities
        Collection<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_SAML_AUTHENTICATED"));
        
        Saml2Authentication authentication = new Saml2Authentication(
                principal, 
                token.getSaml2Response(), 
                authorities);
        
        // Store the authentication result
        responseToken.setAuthentication(authentication);
    }
}