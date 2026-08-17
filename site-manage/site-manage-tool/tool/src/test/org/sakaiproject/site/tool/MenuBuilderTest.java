/**
 * Copyright (c) 2003-2026 The Apereo Foundation
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
package org.sakaiproject.site.tool;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.sakaiproject.cheftool.Context;
import org.sakaiproject.cheftool.api.Menu;
import org.sakaiproject.cheftool.menu.MenuEntry;
import org.sakaiproject.cheftool.menu.MenuImpl;

public class MenuBuilderTest {

    @Test
    public void addMenuToContextOmitsToolbarWithOnlyTheCurrentTab() {
        Menu menu = new MenuImpl();
        MenuEntry current = new MenuEntry("Site Information", "doMenu_siteInfo");
        current.setIsCurrent(true);
        menu.add(current);

        Context context = Mockito.mock(Context.class);
        MenuBuilder.addMenuToContext(menu, context);

        Mockito.verify(context, Mockito.never()).put(Mockito.eq(Menu.CONTEXT_MENU), Mockito.any());
        Assert.assertFalse(MenuBuilder.hasNavigableItems(menu));
    }

    @Test
    public void addMenuToContextKeepsToolbarWhenOtherTabsExist() {
        Menu menu = new MenuImpl();
        MenuEntry current = new MenuEntry("Site Information", "doMenu_siteInfo");
        current.setIsCurrent(true);
        menu.add(current);
        menu.add(new MenuEntry("Manage Groups", "doManageGroupHelper"));

        Context context = Mockito.mock(Context.class);
        MenuBuilder.addMenuToContext(menu, context);

        Mockito.verify(context).put(Menu.CONTEXT_MENU, menu);
        Assert.assertTrue(MenuBuilder.hasNavigableItems(menu));
    }

    @Test
    public void addMenuToContextKeepsSingleNonCurrentTab() {
        Menu menu = new MenuImpl();
        menu.add(new MenuEntry("Search", "doShow_simple_search"));

        Context context = Mockito.mock(Context.class);
        MenuBuilder.addMenuToContext(menu, context);

        Mockito.verify(context).put(Menu.CONTEXT_MENU, menu);
        Assert.assertTrue(MenuBuilder.hasNavigableItems(menu));
    }
}
