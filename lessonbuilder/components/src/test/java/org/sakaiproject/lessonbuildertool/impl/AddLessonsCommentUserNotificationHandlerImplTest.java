/**
 * Copyright (c) 2003-2023 The Apereo Foundation
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
package org.sakaiproject.lessonbuildertool.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.lessonbuildertool.SimplePage;
import org.sakaiproject.lessonbuildertool.SimplePageComment;
import org.sakaiproject.lessonbuildertool.SimplePageImpl;
import org.sakaiproject.lessonbuildertool.model.SimplePageToolDao;

/**
 * Unit tests for comment notification URL generation
 */
public class AddLessonsCommentUserNotificationHandlerImplTest {

    private SimplePageToolDao simplePageToolDao;
    private SimplePageComment comment;
    private SimplePage page;
    private SimplePage topPage;

    @Before
    public void setUp() {
        simplePageToolDao = mock(SimplePageToolDao.class);
        comment = mock(SimplePageComment.class);
        page = new SimplePageImpl();
        page.setPageId(123L);
        
        topPage = new SimplePageImpl();
        topPage.setPageId(100L);
        
        when(comment.getPageId()).thenReturn(123L);
        when(simplePageToolDao.findCommentById(anyLong())).thenReturn(comment);
    }

    @Test
    public void testGetPageUrl_WithTopParent() {
        // Set up a page with a top parent
        page.setTopParent(100L);
        
        when(simplePageToolDao.getPage(123L)).thenReturn(page);
        when(simplePageToolDao.getPageUrl(123L)).thenCallRealMethod();
        
        // Verify that the URL contains the top parent ID
        String url = simplePageToolDao.getPageUrl(123L);
        assertNotNull(url);
        
        // This is a simplified check, as the actual implementation would build a complete URL
        assertEquals(true, url.contains("sendingPage=100"));
    }

    @Test
    public void testGetPageUrl_WithoutTopParent() {
        // Set up a page without a top parent
        page.setTopParent(0L);
        
        when(simplePageToolDao.getPage(123L)).thenReturn(page);
        when(simplePageToolDao.getPageUrl(123L)).thenCallRealMethod();
        
        // Verify that the URL contains the page's own ID
        String url = simplePageToolDao.getPageUrl(123L);
        assertNotNull(url);
        
        // This is a simplified check, as the actual implementation would build a complete URL
        assertEquals(true, url.contains("sendingPage=123"));
    }
}