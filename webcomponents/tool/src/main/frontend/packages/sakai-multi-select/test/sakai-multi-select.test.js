import "../sakai-multi-select.js";
import { elementUpdated, expect, fixture, html } from "@open-wc/testing";
import { enhanceMultiSelect, initSakaiMultiSelects } from "../index.js";

describe("sakai-multi-select tests", () => {

  async function setup(options = {}) {

    const selected = options.selected ?? true;
    const form = await fixture(html`
      <form>
        <label for="members">Assigned Members</label>
        <select
          id="members"
          name="members"
          multiple
          data-sakai-multi-select
          data-sakai-multi-select-placeholder="Find members"
          data-sakai-multi-select-clear-label="Clear members"
          data-sakai-multi-select-select-all-label="Select every match"
          data-sakai-multi-select-no-results="No members found"
          data-sakai-multi-select-remove-label="Remove {0}"
          data-sakai-multi-select-selected-count="{0} chosen"
          data-sakai-multi-select-available-count="{0} available"
          data-sakai-multi-select-selected-label="Added {0}"
          data-sakai-multi-select-removed-label="Dropped {0}"
          data-sakai-multi-select-cleared-label="No members selected"
          data-sakai-multi-select-all-selected-label="Every match selected"
        >
          <option value="alpha">Alpha Student</option>
          <option value="beta" ?selected=${selected}>Beta Student</option>
          <option value="gamma">Gamma Teacher</option>
        </select>
      </form>
    `);
    const select = form.querySelector("select");

    if (options.selectAll) {
      select.dataset.sakaiMultiSelectSelectAll = "true";
    }
    if (options.showClear) {
      select.dataset.sakaiMultiSelectShowClear = "true";
    }

    const el = enhanceMultiSelect(select);
    await elementUpdated(el);

    return { el, form, select };
  }

  async function open(el) {

    el.shadowRoot.querySelector(".sakai-multi-select__control").click();
    await elementUpdated(el);
  }

  async function keydown(el, key) {

    el.shadowRoot.querySelector("input").dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true, composed: true }));
    await elementUpdated(el);
  }

  it("enhances marked native multiple selects and leaves unsupported selects alone", async () => {

    const form = await fixture(html`
      <form>
        <label for="enhanced">Enhanced</label>
        <select id="enhanced" name="enhanced" multiple data-sakai-multi-select>
          <option value="one">One</option>
        </select>
        <select id="single" name="single" data-sakai-multi-select>
          <option value="two">Two</option>
        </select>
      </form>
    `);

    const enhanced = form.querySelector("#enhanced");
    const single = form.querySelector("#single");
    const enhancedElements = initSakaiMultiSelects(form);
    await elementUpdated(enhancedElements[0]);

    expect(enhancedElements).to.have.length(1);
    expect(enhancedElements[0].localName).to.equal("sakai-multi-select");
    expect(enhanced.style.display).to.equal("none");
    expect(enhanced.dataset.sakaiMultiSelectEnhanced).to.equal("true");
    expect(enhanceMultiSelect(single)).to.equal(null);
    expect(single.style.display).to.equal("");
  });

  it("renders initial selected options as removable chips", async () => {

    const { el } = await setup();
    const chip = el.shadowRoot.querySelector(".sakai-multi-select__chip");

    expect(chip.textContent).to.contain("Beta Student");
    expect(chip.querySelector("button").getAttribute("aria-label")).to.equal("Remove Beta Student");
  });

  it("updates the native select and FormData when options are toggled", async () => {

    const { el, form, select } = await setup();
    await open(el);

    el.shadowRoot.querySelectorAll("[role='option']")[0].click();
    await elementUpdated(el);

    expect(select.options[0].selected).to.equal(true);
    expect(new FormData(form).getAll("members")).to.deep.equal([ "alpha", "beta" ]);
  });

  it("dispatches a bubbling change event from the native select", async () => {

    const { el, select } = await setup({ selected: false });
    let changes = 0;
    select.addEventListener("change", () => changes++);
    await open(el);

    el.shadowRoot.querySelectorAll("[role='option']")[0].click();
    await elementUpdated(el);

    expect(changes).to.equal(1);
  });

  it("filters options and shows the localized no-results state", async () => {

    const { el } = await setup();
    const input = el.shadowRoot.querySelector("input");
    await open(el);

    input.value = "teacher";
    input.dispatchEvent(new InputEvent("input", { bubbles: true, composed: true }));
    await elementUpdated(el);

    expect(el.shadowRoot.querySelectorAll("[role='option']")).to.have.length(1);
    expect(el.shadowRoot.querySelector("[role='option']").textContent).to.contain("Gamma Teacher");

    input.value = "nobody";
    input.dispatchEvent(new InputEvent("input", { bubbles: true, composed: true }));
    await elementUpdated(el);

    expect(el.shadowRoot.querySelector(".sakai-multi-select__no-results").textContent).to.contain("No members found");
  });

  it("clears selections and optionally selects all filtered options", async () => {

    const { el, select } = await setup({ selected: false, selectAll: true, showClear: true });
    await open(el);

    el.shadowRoot.querySelector(".sakai-multi-select__action").click();
    await elementUpdated(el);

    expect(Array.from(select.selectedOptions).map(option => option.value)).to.deep.equal([ "alpha", "beta", "gamma" ]);
    expect(el.shadowRoot.querySelector(".sakai-multi-select__status").textContent).to.equal("Every match selected");

    el.shadowRoot.querySelectorAll(".sakai-multi-select__action")[1].click();
    await elementUpdated(el);

    expect(Array.from(select.selectedOptions)).to.have.length(0);
    expect(el.shadowRoot.querySelector(".sakai-multi-select__status").textContent).to.equal("No members selected");
  });

  it("hides the visible clear-all action by default", async () => {

    const { el } = await setup();
    await open(el);

    expect(el.shadowRoot.querySelector(".sakai-multi-select__action")).to.not.exist;
    expect(el.shadowRoot.querySelector(".sakai-multi-select__chip-remove")).to.exist;
  });

  it("supports keyboard opening, navigation, selection, close, and Backspace chip removal", async () => {

    const { el, select } = await setup({ selected: false });
    const input = el.shadowRoot.querySelector("input");

    await keydown(el, "ArrowDown");
    expect(input.getAttribute("aria-expanded")).to.equal("true");
    expect(input.getAttribute("aria-activedescendant")).to.equal(el.shadowRoot.querySelectorAll("[role='option']")[0].id);

    await keydown(el, " ");
    expect(select.options[0].selected).to.equal(true);

    await keydown(el, "ArrowDown");
    await keydown(el, "Enter");
    expect(select.options[1].selected).to.equal(true);

    await keydown(el, "Escape");
    expect(input.getAttribute("aria-expanded")).to.equal("false");

    await keydown(el, "Backspace");
    expect(select.options[1].selected).to.equal(false);
    expect(select.options[0].selected).to.equal(true);
  });

  it("sets required combobox, listbox, option, and live-region ARIA attributes", async () => {

    const { el } = await setup({ selected: false });
    const input = el.shadowRoot.querySelector("input");
    await open(el);

    const listbox = el.shadowRoot.querySelector("[role='listbox']");
    const option = el.shadowRoot.querySelector("[role='option']");

    expect(input.getAttribute("role")).to.equal("combobox");
    expect(input.getAttribute("aria-expanded")).to.equal("true");
    expect(input.getAttribute("aria-controls")).to.equal(listbox.id);
    expect(input.getAttribute("aria-label")).to.equal("Assigned Members");
    expect(listbox.getAttribute("aria-multiselectable")).to.equal("true");
    expect(option.getAttribute("aria-selected")).to.equal("false");

    option.click();
    await elementUpdated(el);

    expect(option.getAttribute("aria-selected")).to.equal("true");
    expect(el.shadowRoot.querySelector("[aria-live='polite']").textContent).to.equal("Added Alpha Student");
  });
});
