/**********************************************************************************
 * Copyright (c) 2025 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.tool.impl;

import java.io.Serializable;

import org.apache.ignite.IgniteCache;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.ignite.EagerIgniteSpringBean;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple Ignite-backed session data store for clusterable session attributes.
 * This is a helper component that can store serializable data in Ignite cache.
 */
@Slf4j
public class IgniteSessionStore {
    
    private static IgniteCache<String, SessionData> sessionCache;
    private static boolean initialized = false;
    
    private static void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            EagerIgniteSpringBean ignite = ComponentManager.get(EagerIgniteSpringBean.class);
            if (ignite != null) {
                sessionCache = ignite.getOrCreateCache("sakai_sessions");
                log.info("Ignite session store initialized successfully");
            }
        } catch (Exception e) {
            log.debug("Ignite session cache not available: {}", e.getMessage());
            sessionCache = null;
        }
    }
    
    public static boolean isAvailable() {
        initialize();
        return sessionCache != null;
    }
    
    /**
     * Store serializable data in Ignite if possible
     */
    public static void storeSerializableAttribute(String sessionId, String name, Serializable value) {
        initialize();
        if (sessionCache == null) {
            return;
        }
        
        try {
            SessionData data = sessionCache.get(sessionId);
            if (data == null) {
                data = new SessionData(sessionId);
            }
            data.setAttribute(name, value);
            sessionCache.put(sessionId, data);
        } catch (Exception e) {
            log.debug("Failed to store session attribute in Ignite cache: {}", name, e);
        }
    }
    
    /**
     * Retrieve serializable data from Ignite if available
     */
    public static Serializable getSerializableAttribute(String sessionId, String name) {
        initialize();
        if (sessionCache == null) {
            return null;
        }
        
        try {
            SessionData data = sessionCache.get(sessionId);
            return data != null ? data.getAttribute(name) : null;
        } catch (Exception e) {
            log.debug("Failed to retrieve session attribute from Ignite cache: {}", name, e);
            return null;
        }
    }
    
    /**
     * Remove serializable data from Ignite
     */
    public static void removeSerializableAttribute(String sessionId, String name) {
        initialize();
        if (sessionCache == null) {
            return;
        }
        
        try {
            SessionData data = sessionCache.get(sessionId);
            if (data != null) {
                data.removeAttribute(name);
                if (data.getAttributes().isEmpty()) {
                    sessionCache.remove(sessionId);
                } else {
                    sessionCache.put(sessionId, data);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to remove session attribute from Ignite cache: {}", name, e);
        }
    }
    
    /**
     * Invalidate entire session from Ignite
     */
    public static void invalidateSession(String sessionId) {
        initialize();
        if (sessionCache != null) {
            try {
                sessionCache.remove(sessionId);
            } catch (Exception e) {
                log.debug("Failed to invalidate session in Ignite cache: {}", sessionId, e);
            }
        }
    }
}