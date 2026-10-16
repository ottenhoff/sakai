/*
 * Copyright (c) 2026 The Apereo Foundation
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Real browser and production assets, with rendered-markup fixtures; no Sakai server. */
class SamigoScriptAssetsTest {

    private static final Path WEBAPP = Path.of("../samigo/samigo-app/src/webapp");

    @Test
    void printPreviewResizesWithoutReloadingAndAppliesSettings() throws IOException {
        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch()) {
            Page page = fixture(browser, "jsf/print/printAssessment.jsp", "assessment-print-preview.js", """
                <span id="pdf-preview-url"> https://samigo.invalid/preview </span>
                <span id="pdf-preview-title"> Assessment preview </span>
                <iframe id="print-pdf-preview" style="width:100%"></iframe>
                <form onsubmit="event.preventDefault(); this.dataset.submissions =
                    Number(this.dataset.submissions || 0) + 1; this.dataset.submitter = event.submitter.id;">
                  <select class="form-select"><option>A</option><option>B</option></select>
                  <button id="apply" class="auto-apply-settings">Apply</button>
                </form>
                """);
            AtomicInteger previewRequests = new AtomicInteger();
            page.route("**/preview", route -> {
                previewRequests.incrementAndGet();
                route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("Preview"));
            });
            page.navigate("https://samigo.invalid/fixture");
            assertThat(page.locator("#print-pdf-preview")).hasAttribute("title", "Assessment preview");
            assertThat(page.locator("#print-pdf-preview")).hasAttribute("src", "https://samigo.invalid/preview");
            assertEquals(1, previewRequests.get());
            page.setViewportSize(1200, 900);
            page.waitForFunction("parseFloat(document.getElementById('print-pdf-preview').style.height) === "
                    + "Math.max(600, document.getElementById('print-pdf-preview').offsetWidth * 0.75)");
            assertEquals(1, previewRequests.get());
            page.locator("select").selectOption("B");
            assertThat(page.locator("form")).hasAttribute("data-submissions", "1");
            assertThat(page.locator("form")).hasAttribute("data-submitter", "apply");
        }
    }

    @Test
    void attachmentButtonsInvokeTheirHiddenLinkAndPreventFormSubmission() throws IOException {
        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch()) {
            Page page = fixture(browser, "jsf/evaluation/gradeStudentResultAttachment.jsp",
                    "evaluation-attachments.js", """
                <form onsubmit="event.preventDefault(); this.dataset.submitted = 'true'">
                  <a href="#" id="grade:hiddenlink" onclick="event.preventDefault();
                    this.dataset.clicks = Number(this.dataset.clicks || 0) + 1;">Hidden link</a>
                  <button id="grade:addAttachments" onclick="addAttachments(event, this)">Add</button>
                  <button id="grade:addRemoveAttachments" onclick="addRemoveAttachments(event, this)">Edit</button>
                </form>
                """);
            page.navigate("https://samigo.invalid/fixture");
            page.getByText("Add", new Page.GetByTextOptions().setExact(true)).click();
            page.getByText("Edit", new Page.GetByTextOptions().setExact(true)).click();
            assertThat(page.locator("a")).hasAttribute("data-clicks", "2");
            assertNull(page.locator("form").getAttribute("data-submitted"));
        }
    }

    @Test
    void submissionReviewStillCallsTheMatchingJsfLinkHandler() throws IOException {
        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch()) {
            Page page = fixture(browser, "jsf/delivery/submitted.jsp", "assessment-submission.js", """
                <a id="other" href="#" onclick="this.dataset.called = 'true'">Other link</a>
                <a id="submitted:hiddenlink" href="#" onclick="this.dataset.called = 'true'">Review link</a>
                <button id="submitted:reviewAssessment" onclick="reviewAssessment(this)">Review</button>
                """);
            page.navigate("https://samigo.invalid/fixture");
            page.getByText("Review", new Page.GetByTextOptions().setExact(true)).click();
            assertThat(page.locator("a").nth(1)).hasAttribute("data-called", "true");
            assertNull(page.locator("#other").getAttribute("data-called"));
        }
    }

    private Page fixture(Browser browser, String view, String asset, String markup) throws IOException {
        String script = "<script src=\"/samigo-app/js/" + asset + "\"></script>";
        assertTrue(Files.readString(WEBAPP.resolve(view)).contains(script));
        Page page = browser.newPage();
        page.route("https://samigo.invalid/fixture", route -> route.fulfill(
                new Route.FulfillOptions().setContentType("text/html")
                        .setBody("<!doctype html><html><head>" + script + "</head><body>"
                                + markup + "</body></html>")));
        page.route("https://samigo.invalid/samigo-app/js/" + asset, route -> route.fulfill(
                new Route.FulfillOptions().setContentType("text/javascript")
                        .setPath(WEBAPP.resolve("js/" + asset))));
        return page;
    }
}
