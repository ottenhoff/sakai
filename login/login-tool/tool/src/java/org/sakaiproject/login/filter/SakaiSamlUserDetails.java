package org.sakaiproject.login.filter;

import org.sakaiproject.component.cover.ServerConfigurationService;

import org.opensaml.saml2.core.Attribute;
import org.opensaml.xml.XMLObject;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.userdetails.SAMLUserDetailsService;

public class SakaiSamlUserDetails implements SAMLUserDetailsService {

        @Override
        public Object loadUserBySAML(SAMLCredential cred) throws UsernameNotFoundException {
                return cred.getAttributeAsString(ServerConfigurationService.getString("saml.username.attribute", "username"));
        }
}
