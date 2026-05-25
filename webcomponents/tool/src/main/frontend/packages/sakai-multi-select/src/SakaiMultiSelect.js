import { SakaiShadowElement } from "@sakai-ui/sakai-element";
import { css, html, nothing } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

const enhancedSelects = new WeakMap();

const defaultStrings = {
  placeholder: "Search...",
  clearLabel: "Clear selections",
  selectAllLabel: "Select all",
  searchLabel: "Search options",
  listLabel: "Options",
  noResults: "No matches found",
  removeLabel: "Remove {0}",
  selectedCount: "{0} selected",
  availableCount: "{0} options available",
  selectedLabel: "Selected {0}",
  removedLabel: "Removed {0}",
  clearedLabel: "Selections cleared",
  allSelectedLabel: "All options selected",
};

let nextId = 0;

export class SakaiMultiSelect extends SakaiShadowElement {

  static properties = {
    _activeIndex: { state: true },
    _disabled: { state: true },
    _items: { state: true },
    _label: { state: true },
    _open: { state: true },
    _query: { state: true },
    _showClear: { state: true },
    _showSelectAll: { state: true },
    _status: { state: true },
    _strings: { state: true },
  };

  constructor() {

    super();

    this._activeIndex = -1;
    this._componentId = `sakai-multi-select-${nextId++}`;
    this._config = {};
    this._disabled = false;
    this._items = [];
    this._label = "";
    this._listboxId = `${this._componentId}-listbox`;
    this._open = false;
    this._query = "";
    this._showClear = false;
    this._showSelectAll = false;
    this._status = "";
    this._statusId = `${this._componentId}-status`;
    this._strings = { ...defaultStrings };

    this._handleDocumentPointerDown = this._handleDocumentPointerDown.bind(this);
    this._handleNativeChange = this._handleNativeChange.bind(this);
  }

  connectedCallback() {

    super.connectedCallback();
    document.addEventListener("pointerdown", this._handleDocumentPointerDown);
    this._startObservingSelect();
  }

  disconnectedCallback() {

    document.removeEventListener("pointerdown", this._handleDocumentPointerDown);
    this._stopObservingSelect();
    this._unbindLabelClick();
    this._selectElement?.removeEventListener("change", this._handleNativeChange);
    super.disconnectedCallback();
  }

  configure(selectElement, options = {}) {

    if (!(selectElement instanceof HTMLSelectElement) || !selectElement.multiple) {
      throw new Error("sakai-multi-select can only enhance a multiple select element.");
    }

    this._selectElement?.removeEventListener("change", this._handleNativeChange);
    this._selectElement = selectElement;
    this._config = { ...options };
    this._selectElement.addEventListener("change", this._handleNativeChange);
    this._bindLabelClick();
    this.refresh();
    this._startObservingSelect();
  }

  focus(options) {

    this.updateComplete.then(() => this.shadowRoot?.querySelector("input")?.focus(options));
  }

  refresh() {

    if (!this._selectElement) {
      return;
    }

    this._disabled = this._selectElement.disabled;
    this._label = this._readLabel();
    this._strings = this._readStrings();
    this._showClear = this._readBooleanOption("showClear", false);
    this._showSelectAll = this._readBooleanOption("selectAll", false);
    this._items = Array.from(this._selectElement.options).map((option, index) => ({
      disabled: option.disabled,
      id: `${this._componentId}-option-${index}`,
      index,
      label: option.label || option.textContent.trim(),
      selected: option.selected,
      value: option.value,
    }));
    this._setActiveToFirst();
  }

  get _selectedItems() {

    return this._items.filter(item => item.selected);
  }

  get _filteredItems() {

    const query = this._query.trim().toLocaleLowerCase();
    return query
      ? this._items.filter(item => item.label.toLocaleLowerCase().includes(query))
      : this._items;
  }

  get _enabledFilteredItems() {

    return this._filteredItems.filter(item => !item.disabled);
  }

  get _activeItem() {

    return this._items[this._activeIndex];
  }

  get _activeDescendant() {

    const activeItem = this._activeItem;
    return this._open && activeItem && this._filteredItems.includes(activeItem) ? activeItem.id : undefined;
  }

  render() {

    const selectedItems = this._selectedItems;
    const filteredItems = this._filteredItems;
    const hasSelections = selectedItems.length > 0;
    const selectedCount = this._format(this._strings.selectedCount, { count: selectedItems.length });

    return html`
      <div class="sakai-multi-select ${this._disabled ? "sakai-multi-select--disabled" : ""}">
        <div class="sakai-multi-select__control" @click=${this._focusAndOpen}>
          ${hasSelections ? html`
            <ul class="sakai-multi-select__chips" aria-label=${selectedCount}>
              ${selectedItems.map(item => html`
                <li class="sakai-multi-select__chip">
                  <span class="sakai-multi-select__chip-label">${item.label}</span>
                  <button
                    type="button"
                    class="sakai-multi-select__chip-remove"
                    aria-label=${this._format(this._strings.removeLabel, { label: item.label })}
                    ?disabled=${this._disabled}
                    @click=${event => this._removeItem(event, item.index)}
                  >
                    <span aria-hidden="true">&times;</span>
                  </button>
                </li>
              `)}
            </ul>
          ` : nothing}

          <input
            aria-activedescendant=${ifDefined(this._activeDescendant)}
            aria-autocomplete="list"
            aria-controls=${this._listboxId}
            aria-describedby=${this._statusId}
            aria-expanded=${this._open ? "true" : "false"}
            aria-label=${this._label || this._strings.searchLabel}
            autocomplete="off"
            ?disabled=${this._disabled}
            class="sakai-multi-select__input"
            placeholder=${hasSelections ? "" : this._strings.placeholder}
            role="combobox"
            .value=${this._query}
            @focus=${this._handleFocus}
            @input=${this._handleInput}
            @keydown=${this._handleKeyDown}
          />
        </div>

        <div class="sakai-multi-select__popup" ?hidden=${!this._open} @mousedown=${this._keepFocus}>
          ${this._showSelectAll || this._showClear ? html`
            <div class="sakai-multi-select__actions">
              ${this._showSelectAll ? html`
                <button type="button" class="sakai-multi-select__action" @click=${this._selectAllFiltered}>
                  ${this._strings.selectAllLabel}
                </button>
              ` : nothing}
              ${this._showClear ? html`
                <button
                  type="button"
                  class="sakai-multi-select__action"
                  ?disabled=${!hasSelections}
                  @click=${this._clearSelections}
                >
                  ${this._strings.clearLabel}
                </button>
              ` : nothing}
            </div>
          ` : nothing}

          <ul
            id=${this._listboxId}
            class="sakai-multi-select__listbox"
            role="listbox"
            aria-label=${this._label || this._strings.listLabel}
            aria-multiselectable="true"
          >
            ${filteredItems.length > 0 ? filteredItems.map(item => html`
              <li
                id=${item.id}
                class="sakai-multi-select__option ${item.index === this._activeIndex ? "sakai-multi-select__option--active" : ""}"
                role="option"
                aria-disabled=${item.disabled ? "true" : "false"}
                aria-selected=${item.selected ? "true" : "false"}
                @click=${() => this._toggleItem(item.index)}
                @mousemove=${() => this._setActiveIndex(item.index)}
              >
                <span class="sakai-multi-select__option-label">${item.label}</span>
              </li>
            `) : html`
              <li class="sakai-multi-select__no-results" role="presentation">${this._strings.noResults}</li>
            `}
          </ul>
        </div>

        <div id=${this._statusId} class="sakai-multi-select__status" aria-live="polite">${this._status}</div>
      </div>
    `;
  }

  _focusAndOpen() {

    if (!this._disabled) {
      this.shadowRoot.querySelector("input")?.focus();
      this._openList();
    }
  }

  _handleFocus() {

    if (!this._disabled) {
      this._announceAvailableCount();
    }
  }

  _handleInput(event) {

    this._query = event.target.value;
    this._openList();
    this._setActiveToFirst();
    this._announceAvailableCount();
  }

  _handleKeyDown(event) {

    if (this._disabled) {
      return;
    }

    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        if (this._open) {
          this._moveActive(1);
        } else {
          this._openList();
          this._setActiveToFirst();
        }
        break;
      case "ArrowUp":
        event.preventDefault();
        if (this._open) {
          this._moveActive(-1);
        } else {
          this._openList();
          this._setActiveIndex(this._enabledFilteredItems.at(-1)?.index ?? -1);
        }
        break;
      case "Home":
        if (this._open) {
          event.preventDefault();
          this._setActiveIndex(this._enabledFilteredItems.at(0)?.index ?? -1);
        }
        break;
      case "End":
        if (this._open) {
          event.preventDefault();
          this._setActiveIndex(this._enabledFilteredItems.at(-1)?.index ?? -1);
        }
        break;
      case "Enter":
        event.preventDefault();
        if (this._open) {
          this._toggleActiveItem();
        } else {
          this._openList();
        }
        break;
      case " ":
        if (this._open && this._activeIndex >= 0) {
          event.preventDefault();
          this._toggleActiveItem();
        }
        break;
      case "Escape":
        event.preventDefault();
        this._closeList();
        break;
      case "Backspace":
        if (!this._query) {
          this._removeLastSelected();
        }
        break;
      case "Tab":
        this._closeList();
        break;
      default:
    }
  }

  _handleDocumentPointerDown(event) {

    if (!event.composedPath().includes(this)) {
      this._closeList();
    }
  }

  _handleNativeChange() {

    if (!this._syncingNativeSelect) {
      this.refresh();
    }
  }

  _keepFocus(event) {

    event.preventDefault();
  }

  _openList() {

    if (this._disabled) {
      return;
    }

    this._open = true;
    if (this._activeIndex < 0 || !this._filteredItems.includes(this._activeItem)) {
      this._setActiveToFirst();
    }
  }

  _closeList() {

    this._open = false;
  }

  _moveActive(delta) {

    const enabledItems = this._enabledFilteredItems;
    if (enabledItems.length === 0) {
      this._setActiveIndex(-1);
      return;
    }

    const currentPosition = enabledItems.findIndex(item => item.index === this._activeIndex);
    const nextPosition = currentPosition < 0
      ? (delta > 0 ? 0 : enabledItems.length - 1)
      : (currentPosition + delta + enabledItems.length) % enabledItems.length;
    this._setActiveIndex(enabledItems[nextPosition].index);
  }

  _setActiveToFirst() {

    this._setActiveIndex(this._enabledFilteredItems.at(0)?.index ?? -1);
  }

  _setActiveIndex(index) {

    this._activeIndex = index;
  }

  _toggleActiveItem() {

    if (this._activeIndex >= 0) {
      this._toggleItem(this._activeIndex);
    }
  }

  _toggleItem(index) {

    const item = this._items[index];
    if (!item || item.disabled) {
      return;
    }

    this._setItemSelected(index, !item.selected);
  }

  _removeItem(event, index) {

    event.stopPropagation();
    this._setItemSelected(index, false);
    this.shadowRoot.querySelector("input")?.focus();
  }

  _removeLastSelected() {

    const selectedItem = this._selectedItems.at(-1);
    if (selectedItem) {
      this._setItemSelected(selectedItem.index, false);
    }
  }

  _setItemSelected(index, selected) {

    const item = this._items[index];
    if (!item || item.disabled || item.selected === selected) {
      return;
    }

    this._items = this._items.map(currentItem => currentItem.index === index
      ? { ...currentItem, selected }
      : currentItem);
    this._syncSelectToItems();
    this._announce(this._format(selected ? this._strings.selectedLabel : this._strings.removedLabel, { label: item.label }));
  }

  _clearSelections() {

    if (this._selectedItems.length === 0) {
      return;
    }

    this._items = this._items.map(item => item.selected ? { ...item, selected: false } : item);
    this._syncSelectToItems();
    this._announce(this._strings.clearedLabel);
    this.shadowRoot.querySelector("input")?.focus();
  }

  _selectAllFiltered() {

    const selectableIndexes = new Set(this._enabledFilteredItems.map(item => item.index));
    if (selectableIndexes.size === 0) {
      return;
    }

    this._items = this._items.map(item => selectableIndexes.has(item.index) ? { ...item, selected: true } : item);
    this._syncSelectToItems();
    this._announce(this._strings.allSelectedLabel);
    this.shadowRoot.querySelector("input")?.focus();
  }

  _syncSelectToItems() {

    if (!this._selectElement) {
      return;
    }

    this._syncingNativeSelect = true;
    this._items.forEach(item => {
      this._selectElement.options[item.index].selected = item.selected;
    });
    this._selectElement.dispatchEvent(new Event("change", { bubbles: true }));
    this._syncingNativeSelect = false;
  }

  _startObservingSelect() {

    if (!this._selectElement || this._selectObserver) {
      return;
    }

    this._selectObserver = new MutationObserver(() => {
      if (!this._syncingNativeSelect) {
        this.refresh();
      }
    });
    this._selectObserver.observe(this._selectElement, { attributes: true, childList: true, subtree: true });
  }

  _stopObservingSelect() {

    this._selectObserver?.disconnect();
    this._selectObserver = undefined;
  }

  _bindLabelClick() {

    this._unbindLabelClick();
    const labels = Array.from(this._selectElement?.labels || []);
    this._labelClickHandler = event => {
      event.preventDefault();
      this.focus();
    };
    labels.forEach(label => label.addEventListener("click", this._labelClickHandler));
    this._unbindLabelClick = () => labels.forEach(label => label.removeEventListener("click", this._labelClickHandler));
  }

  _unbindLabelClick() {}

  _readLabel() {

    const configuredLabel = this._config.label
      || this._dataValue("label")
      || this._selectElement.getAttribute("aria-label")
      || this._textFromLabelledBy()
      || Array.from(this._selectElement.labels || []).map(label => label.textContent.trim()).find(Boolean);

    return configuredLabel || "";
  }

  _textFromLabelledBy() {

    const labelledBy = this._selectElement.getAttribute("aria-labelledby");
    if (!labelledBy) {
      return "";
    }

    return labelledBy
      .split(/\s+/)
      .map(id => this._selectElement.ownerDocument.getElementById(id)?.textContent.trim())
      .filter(Boolean)
      .join(" ");
  }

  _readStrings() {

    return Object.fromEntries(Object.entries(defaultStrings).map(([ key, value ]) => [
      key,
      this._config[key] || this._dataValue(key) || value,
    ]));
  }

  _readBooleanOption(name, defaultValue) {

    if (Object.prototype.hasOwnProperty.call(this._config, name)) {
      return Boolean(this._config[name]);
    }

    const dataValue = this._dataValue(name) ?? this._selectElement.dataset[name];
    if (dataValue === undefined) {
      return defaultValue;
    }

    return dataValue !== "false";
  }

  _dataValue(name) {

    const dataName = `sakaiMultiSelect${name[0].toUpperCase()}${name.slice(1)}`;
    return this._selectElement?.dataset[dataName];
  }

  _format(template, values) {

    const replacement = values.label ?? values.count ?? "";
    return template
      .replaceAll("{label}", values.label ?? "")
      .replaceAll("{count}", values.count ?? "")
      .replaceAll("{0}", replacement)
      .replaceAll("{}", replacement);
  }

  _announceAvailableCount() {

    this._announce(this._format(this._strings.availableCount, { count: this._enabledFilteredItems.length }));
  }

  _announce(message) {

    this._status = message;
  }

  static styles = css`
    :host {
      display: block;
      max-width: 100%;
    }

    .sakai-multi-select {
      position: relative;
      max-width: 100%;
      color: var(--sakai-text-color-1, #212529);
      font-size: var(--default-font-size, 1rem);
    }

    .sakai-multi-select__control {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px;
      min-height: 40px;
      padding: 4px 8px;
      border: 1px solid var(--sakai-border-color, #8a8f94);
      border-radius: 4px;
      background: var(--sakai-background-color-1, #fff);
      cursor: text;
    }

    .sakai-multi-select__control:focus-within {
      border-color: var(--focus-outline-color, #0d6efd);
      box-shadow: 0 0 0 3px var(--sakai-focus-shadow-color, #86b7fe);
    }

    .sakai-multi-select--disabled .sakai-multi-select__control {
      background: var(--sakai-background-color-3, #e9ecef);
      cursor: not-allowed;
      opacity: 0.75;
    }

    .sakai-multi-select__chips {
      display: contents;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .sakai-multi-select__chip {
      display: inline-flex;
      align-items: center;
      max-width: 100%;
      min-height: 28px;
      border: 1px solid var(--sakai-border-color, #8a8f94);
      border-radius: 4px;
      background: var(--sakai-background-color-2, #f4f6f8);
      color: var(--sakai-text-color-1, #212529);
      overflow: hidden;
    }

    .sakai-multi-select__chip-label {
      min-width: 0;
      padding: 2px 6px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .sakai-multi-select__chip-remove {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      min-width: 28px;
      min-height: 28px;
      border: 0;
      border-inline-start: 1px solid var(--sakai-border-color, #8a8f94);
      background: transparent;
      color: inherit;
      cursor: pointer;
      font: inherit;
    }

    .sakai-multi-select__chip-remove:hover,
    .sakai-multi-select__chip-remove:focus-visible {
      background: var(--sakai-background-color-3, #e9ecef);
      outline: none;
    }

    .sakai-multi-select__input {
      flex: 1 1 160px;
      min-width: 120px;
      min-height: 30px;
      border: 0;
      outline: 0;
      background: transparent;
      color: inherit;
      font: inherit;
    }

    .sakai-multi-select__input::placeholder {
      color: var(--sakai-text-color-3, #6c757d);
      opacity: 1;
    }

    .sakai-multi-select__popup {
      position: absolute;
      z-index: 1000;
      inset-block-start: calc(100% + 4px);
      inset-inline: 0;
      border: 1px solid var(--sakai-border-color, #8a8f94);
      border-radius: 4px;
      background: var(--sakai-background-color-1, #fff);
      box-shadow: 0 6px 18px rgb(0 0 0 / 18%);
    }

    .sakai-multi-select__popup[hidden] {
      display: none;
    }

    .sakai-multi-select__actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      padding: 6px 8px;
      border-block-end: 1px solid var(--sakai-border-color, #d0d5da);
    }

    .sakai-multi-select__action {
      min-height: 32px;
      padding: 2px 8px;
      border: 1px solid var(--button-border-color, var(--sakai-border-color, #8a8f94));
      border-radius: 4px;
      background: var(--button-background, #fff);
      color: var(--button-text-color, #212529);
      cursor: pointer;
      font: inherit;
    }

    .sakai-multi-select__action:hover,
    .sakai-multi-select__action:focus-visible {
      background: var(--button-hover-background, #f4f6f8);
      outline: 2px solid var(--focus-outline-color, #0d6efd);
      outline-offset: 1px;
    }

    .sakai-multi-select__action:disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }

    .sakai-multi-select__listbox {
      max-height: 240px;
      margin: 0;
      padding: 4px 0;
      overflow-y: auto;
      list-style: none;
    }

    .sakai-multi-select__option,
    .sakai-multi-select__no-results {
      padding: 8px 10px;
    }

    .sakai-multi-select__option {
      cursor: pointer;
    }

    .sakai-multi-select__option[aria-selected="true"] {
      background: var(--sakai-active-color-2, #e7f1ff);
      font-weight: 600;
    }

    .sakai-multi-select__option--active {
      outline: 2px solid var(--focus-outline-color, #0d6efd);
      outline-offset: -2px;
    }

    .sakai-multi-select__option[aria-disabled="true"] {
      color: var(--sakai-text-color-3, #6c757d);
      cursor: not-allowed;
      opacity: 0.65;
    }

    .sakai-multi-select__option-label {
      display: block;
      overflow-wrap: anywhere;
    }

    .sakai-multi-select__no-results {
      color: var(--sakai-text-color-3, #6c757d);
    }

    .sakai-multi-select__status {
      position: absolute;
      width: 1px;
      height: 1px;
      margin: -1px;
      padding: 0;
      border: 0;
      clip: rect(0 0 0 0);
      clip-path: inset(50%);
      overflow: hidden;
      white-space: nowrap;
    }
  `;
}

export function defineSakaiMultiSelect() {

  if (!customElements.get("sakai-multi-select")) {
    customElements.define("sakai-multi-select", SakaiMultiSelect);
  }
}

export function enhanceMultiSelect(selectElement, options = {}) {

  if (!(selectElement instanceof HTMLSelectElement) || !selectElement.multiple) {
    return null;
  }

  const existingElement = enhancedSelects.get(selectElement);
  if (existingElement?.isConnected) {
    return existingElement;
  }

  defineSakaiMultiSelect();

  const element = document.createElement("sakai-multi-select");

  try {
    element.configure(selectElement, options);
    selectElement.insertAdjacentElement("afterend", element);
    enhancedSelects.set(selectElement, element);
    selectElement.dataset.sakaiMultiSelectEnhanced = "true";
    selectElement.classList.add("sakai-multi-select-native");
    selectElement.style.display = "none";
    selectElement.setAttribute("aria-hidden", "true");
    selectElement.tabIndex = -1;
    return element;
  } catch (error) {
    element.remove();
    console.error("Unable to enhance multi-select", error);
    return null;
  }
}

export function initSakaiMultiSelects(root = document) {

  return Array.from(root.querySelectorAll("select[multiple][data-sakai-multi-select]"))
    .map(selectElement => enhanceMultiSelect(selectElement))
    .filter(Boolean);
}
