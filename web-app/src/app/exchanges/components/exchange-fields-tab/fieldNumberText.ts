/**
 * A numeric answer is carried as the digits a responder entered, never as a JavaScript number.
 *
 * A stored field value can hold 28 digits before the decimal point and 10 after it, which is far
 * wider than a JavaScript number can represent, so converting through `Number` silently rewrites
 * the answer. Trailing fraction zeros are kept because they are what a configured scale looks like.
 */

const NUMERIC_TEXT = /^-?(\d+(\.\d+)?|\.\d+)([eE][+-]?\d+)?$/;

/** The exact digits of a numeric entry, or null when the field holds no answer. */
export const toExactNumberText = (value: unknown): string | null =>
{
    if (value === null || value === undefined) return null;

    const trimmed = String(value).trim();

    if (trimmed === '') return null;

    // A number input can hand back a leading plus, a dangling point mid-typing, or a bare fraction.
    const normalized = (trimmed.startsWith('+') ? trimmed.slice(1) : trimmed)
        .replace(/\.$/, '')
        .replace(/^\./, '0.')
        .replace(/^-\./, '-0.');

    return NUMERIC_TEXT.test(normalized) ? normalized : null;
};

/** Groups a numeric answer for display, digit for digit, with its fraction exactly as stored. */
export const formatExactNumber = (text: string): string =>
{
    const exact = toExactNumberText(text);

    if (exact === null || /[eE]/.test(exact)) return text;

    const negative = exact.startsWith('-');
    const [whole, fraction] = (negative ? exact.slice(1) : exact).split('.');
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

    return `${negative ? '-' : ''}${grouped}${fraction === undefined ? '' : `.${fraction}`}`;
};
