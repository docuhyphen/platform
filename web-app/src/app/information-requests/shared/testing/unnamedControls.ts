const NAMED_SELECTOR = [
    "button",
    "a[href]",
    "input:not([type='hidden'])",
    "select",
    "textarea",
    "section",
    "[role='button']",
    "[role='checkbox']",
    "[role='radio']",
    "[role='combobox']",
    "[role='tab']",
    "[role='switch']",
    "[role='region']",
    "[role='dialog']",
    "[role='progressbar']",
    "[role='searchbox']",
].join(",");

const TEXT_NAMED = new Set(["BUTTON", "A"]);
const TEXT_NAMED_ROLES = new Set(["button", "tab", "switch", "checkbox", "radio"]);

const textOf = (element: Element | null): string => element?.textContent?.trim() ?? "";

const hidden = (element: Element): boolean => element.closest("[aria-hidden='true'], [hidden]") !== null;

const labelledByText = (element: Element): string =>
    (element.getAttribute("aria-labelledby") ?? "")
        .split(/\s+/)
        .filter(Boolean)
        .map(id => textOf(element.ownerDocument.getElementById(id)))
        .join(" ")
        .trim();

const labelText = (element: Element): string =>
{
    const id = element.getAttribute("id");
    const forLabel = id ? Array.from(element.ownerDocument.querySelectorAll("label")).find(label => label.htmlFor === id) ?? null : null;
    return textOf(forLabel) || textOf(element.closest("label"));
};

const nameOf = (element: Element): string =>
{
    const role = element.getAttribute("role") ?? "";
    return labelledByText(element)
        || element.getAttribute("aria-label")?.trim()
        || labelText(element)
        || element.getAttribute("title")?.trim()
        || (TEXT_NAMED.has(element.tagName) || TEXT_NAMED_ROLES.has(role) ? textOf(element) : "")
        || (element instanceof HTMLInputElement && ["submit", "button"].includes(element.type) ? element.value.trim() : "");
};

export const unnamedControls = (root: ParentNode): string[] =>
    Array.from(root.querySelectorAll(NAMED_SELECTOR))
        .filter(element => !hidden(element))
        .filter(element => !nameOf(element))
        .map(element => `${element.tagName.toLowerCase()}#${element.getAttribute("id") ?? "(no id)"}`);
