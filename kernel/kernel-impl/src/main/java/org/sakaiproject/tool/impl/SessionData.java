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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Minimal container for serializable session data that can be stored in Ignite cache.
 * Only stores objects that are actually serializable to avoid clustering issues.
 */
public class SessionData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String sessionId;
    private final Map<String, Serializable> attributes = new ConcurrentHashMap<>();
    private final long creationTime;
    private volatile long lastAccessedTime;
    
    public SessionData(String sessionId) {
        this.sessionId = sessionId;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = creationTime;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public Map<String, Serializable> getAttributes() {
        return attributes;
    }
    
    public void setAttribute(String name, Serializable value) {
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
        touch();
    }
    
    public Serializable getAttribute(String name) {
        touch();
        return attributes.get(name);
    }
    
    public void removeAttribute(String name) {
        attributes.remove(name);
        touch();
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }
    
    private void touch() {
        lastAccessedTime = System.currentTimeMillis();
    }
}