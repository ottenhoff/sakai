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
import com.microsoft.playwright.options.AriaRole;
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
        assertThat(page.locator("#groupMembers")).isVisible();
    }

    @Test
    void instructorCurrentSiteInformationTabIsNotALink() {
        sakai.login("instructor1");
        page.navigate(ensureCourseUrl());
        sakai.toolClick("Site Info");

        assertNoTemplateRenderingError();
        Locator currentTab = page.locator(".navIntraTool span.current")
            .filter(new Locator.FilterOptions().setHasText(Pattern.compile("Site Information", Pattern.CASE_INSENSITIVE)))
            .first();
        assertThat(currentTab).isVisible();
        assertThat(currentTab.locator("a")).hasCount(0);

        Locator manageGroups = page.locator(".navIntraTool a")
            .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Manage Groups$", Pattern.CASE_INSENSITIVE)))
            .first();
        assertThat(manageGroups).isVisible();
    }

    @Test
    void studentSiteInfoHasNoClickableSiteInformationTab() {
        sakai.login("instructor1");
        String siteUrl = ensureCourseUrl();

        sakai.login("student0011");
        sakai.gotoPath(siteUrl);
        sakai.toolClick("Site Info");

        assertNoTemplateRenderingError();
        assertThat(page.locator("body")).containsText(Pattern.compile("Site Information|Site Info", Pattern.CASE_INSENSITIVE));
        assertNoClickableSiteInfoToolbar();
    }

    @Test
    void instructorStudentViewSiteInfoHasNoClickableSiteInformationTab() {
        sakai.login("instructor1");
        page.navigate(ensureCourseUrl());

        Locator enterStudentView = page.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions().setName(Pattern.compile("Enter Student View|Student View", Pattern.CASE_INSENSITIVE))).first();
        if (enterStudentView.count() == 0 || !enterStudentView.isVisible()) {
            Locator roleSelect = page.locator("select").filter(new Locator.FilterOptions().setHasText(Pattern.compile("Student", Pattern.CASE_INSENSITIVE))).first();
            if (roleSelect.count() > 0 && roleSelect.isVisible()) {
                roleSelect.selectOption(Pattern.compile("Student", Pattern.CASE_INSENSITIVE));
                page.waitForLoadState();
            } else {
                throw new IllegalStateException("Unable to enter student view from the instructor account");
            }
        } else {
            enterStudentView.click(new Locator.ClickOptions().setForce(true));
            page.waitForLoadState();
        }

        sakai.toolClick("Site Info");
        assertNoTemplateRenderingError();
        assertThat(page.locator("body")).containsText(Pattern.compile("Site Information|Site Info", Pattern.CASE_INSENSITIVE));
        assertNoClickableSiteInfoToolbar();
    }

    private void assertNoClickableSiteInfoToolbar() {
        assertThat(page.locator(".navIntraTool a")).hasCount(0);
        assertThat(page.locator(".dropdown-navIntraTool")).hasCount(0);
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
