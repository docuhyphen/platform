
const NUMERIC_TEXT = /^-?(\d+(\.\d+)?|\.\d+)([eE][+-]?\d+)?$/;

export const toExactNumberText = (value: unknown): string | null =>
{
    if (value === null || value === undefined) return null;

    const trimmed = String(value).trim();

    if (trimmed === '') return null;

    const normalized = (trimmed.startsWith('+') ? trimmed.slice(1) : trimmed)
        .replace(/\.$/, '')
        .replace(/^\./, '0.')
        .replace(/^-\./, '-0.');

    return NUMERIC_TEXT.test(normalized) ? normalized : null;
};

export const formatExactNumber = (text: string): string =>
{
    const exact = toExactNumberText(text);

    if (exact === null || /[eE]/.test(exact)) return text;

    const negative = exact.startsWith('-');
    const [whole, fraction] = (negative ? exact.slice(1) : exact).split('.');
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

    return `${negative ? '-' : ''}${grouped}${fraction === undefined ? '' : `.${fraction}`}`;
};
