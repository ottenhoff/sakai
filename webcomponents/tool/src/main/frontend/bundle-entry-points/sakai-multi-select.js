import { initSakaiMultiSelects } from "@sakai-ui/sakai-multi-select/sakai-multi-select.js";

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", () => initSakaiMultiSelects(), { once: true });
} else {
  initSakaiMultiSelects();
}
