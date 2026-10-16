/*
 * Copyright (c) 2026 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Only resizes the frame. Never touches src: reassigning it navigates the frame,
// which tears down the pdf.js viewer and refetches the PDF on every resize event.
function sizePrintPdfPreview() {
  const iframe = document.getElementById('print-pdf-preview');
  if (iframe) {
    iframe.style.height = Math.max(600, iframe.offsetWidth * 0.75) + 'px';
  }
}

function loadPrintPdfPreview() {
  const iframe = document.getElementById('print-pdf-preview');
  const urlEl = document.getElementById('pdf-preview-url');
  const titleEl = document.getElementById('pdf-preview-title');
  if (iframe && urlEl) {
    iframe.src = urlEl.textContent.trim();
  }
  if (iframe && titleEl) {
    iframe.title = titleEl.textContent.trim();
  }
  sizePrintPdfPreview();
}

function applyPrintSettingsFromControl(control) {
  const form = control.closest('form');
  const applyBtn = form && form.querySelector('.auto-apply-settings');
  if (!form || !applyBtn) {
    return;
  }
  if (typeof form.requestSubmit === 'function') {
    form.requestSubmit(applyBtn);
  } else {
    applyBtn.click();
  }
}

document.addEventListener('DOMContentLoaded', function() {
  loadPrintPdfPreview();
  window.addEventListener('resize', sizePrintPdfPreview);

  document.querySelectorAll('form').forEach(form => {
    form.addEventListener('change', function(event) {
      const control = event.target;
      if (control.matches('.form-check-input, .form-select, input[type="radio"]')) {
        applyPrintSettingsFromControl(control);
      }
    });
  });
});
