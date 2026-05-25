/*
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
package org.sakaiproject.e2e.tests;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.microsoft.playwright.Locator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sakaiproject.e2e.support.SakaiUiTestBase;

class SiteInfoTest extends SakaiUiTestBase {

    private static String sakaiUrl;

    @Test
    void canOpenManageGroupsHelper() {
        sakai.login("instructor1");
        page.navigate(ensureCourseUrl());
        sakai.toolClick("Site Info");

        assertNoTemplateRenderingError();
        assertThat(page.locator("body")).containsText(Pattern.compile("Site Information|Site Info", Pattern.CASE_INSENSITIVE));

        Locator manageGroups = page.locator(".navIntraTool a")
            .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Manage Groups$", Pattern.CASE_INSENSITIVE)))
            .first();
        assertThat(manageGroups).isVisible();
        manageGroups.click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();

        assertNoTemplateRenderingError();
        assertThat(page.locator("body")).containsText(Pattern.compile("Group List|No groups", Pattern.CASE_INSENSITIVE));

        Locator createGroup = page.locator(".navIntraTool a")
            .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Create New Group$", Pattern.CASE_INSENSITIVE)))
            .first();
        assertThat(createGroup).isVisible();
        createGroup.click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();

        assertNoTemplateRenderingError();
        assertThat(page.locator("#creategroup-form")).isVisible();
        assertThat(page.locator("#groupTitle")).isVisible();

        Locator memberPicker = page.locator("sakai-multi-select").first();
        assertThat(memberPicker).isVisible();
        assertThat(page.locator("#groupMembers")).isHidden();

        Locator assignedMembersSearch = memberPicker.locator("input[role='combobox']");
        assertThat(assignedMembersSearch).isVisible();
        assignedMembersSearch.fill("Instructor");

        Locator instructorOption = memberPicker.locator("[role='option']")
            .filter(new Locator.FilterOptions().setHasText(Pattern.compile("Role:.*Instructor", Pattern.CASE_INSENSITIVE)))
            .first();
        assertThat(instructorOption).isVisible();
        instructorOption.click();

        assertThat(memberPicker.locator(".sakai-multi-select__chip")).containsText(Pattern.compile("Instructor", Pattern.CASE_INSENSITIVE));
        assertThat(page.locator("#groupMembers option:checked")).containsText(Pattern.compile("Instructor", Pattern.CASE_INSENSITIVE));

        memberPicker.locator(".sakai-multi-select__chip-remove").first().click();
        assertThat(page.locator("#groupMembers option:checked")).hasCount(0);
    }

    private String ensureCourseUrl() {
        if (sakaiUrl == null || sakaiUrl.isBlank()) {
            sakaiUrl = sakai.createCourse("instructor1", List.of("sakai\\.announcements"));
        }
        return sakaiUrl;
    }

    private void assertNoTemplateRenderingError() {
        String bodyText = page.locator("body").textContent();
        Pattern templateError = Pattern.compile(
            "TemplateProcessingException|TemplateOutputException|An error happened during template rendering",
            Pattern.CASE_INSENSITIVE
        );
        assertFalse(templateError.matcher(bodyText == null ? "" : bodyText).find(), "Manage Groups rendered a Thymeleaf error");
    }
}
