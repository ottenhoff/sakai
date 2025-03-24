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
package org.sakaiproject.login.filter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;

/**
 * Custom SAML logout handler for Sakai that ensures both the HTTP session
 * and Sakai's session are properly invalidated during SAML logout.
 * This replaces the older SakaiLogoutSamlFilter class.
 */
@Slf4j
public class SakaiSamlLogoutFilter extends SecurityContextLogoutHandler implements LogoutHandler {
    
    @Setter private UsageSessionService usageSessionService;
    @Setter private SessionManager sessionManager;
    @Setter private boolean invalidateSakaiSession = true;
    
    /**
     * Gets the SessionManager, falling back to ComponentManager if needed.
     */
    private SessionManager getSessionManager() {
        if (sessionManager == null) {
            sessionManager = org.sakaiproject.component.cover.ComponentManager.get(SessionManager.class);
            if (sessionManager == null) {
                log.error("Unable to get SessionManager from either Spring context or Component Manager");
            } else {
                log.debug("Retrieved SessionManager from Component Manager");
            }
        }
        return sessionManager;
    }
    
    /**
     * Gets the UsageSessionService, falling back to ComponentManager if needed.
     */
    private UsageSessionService getUsageSessionService() {
        if (usageSessionService == null) {
            usageSessionService = org.sakaiproject.component.cover.ComponentManager.get(UsageSessionService.class);
            if (usageSessionService == null) {
                log.error("Unable to get UsageSessionService from either Spring context or Component Manager");
            } else {
                log.debug("Retrieved UsageSessionService from Component Manager");
            }
        }
        return usageSessionService;
    }
    
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        log.debug("Processing Sakai SAML logout");
        
        // First perform Sakai-specific logout
        if (invalidateSakaiSession) {
            try {
                // Clear Sakai session
                SessionManager sm = getSessionManager();
                if (sm != null) {
                    Session session = sm.getCurrentSession();
                    if (session != null) {
                        log.debug("Invalidating Sakai session: {}", session.getId());
                        session.invalidate();
                    }
                }
                
                // Clear usage session data
                UsageSessionService uss = getUsageSessionService();
                if (uss != null) {
                    log.debug("Logging out of usage session service");
                    uss.logout();
                }
            } catch (Exception e) {
                log.error("Error invalidating Sakai session during SAML logout", e);
            }
        }
        
        // Then perform standard Spring Security logout
        super.logout(request, response, authentication);
    }
}