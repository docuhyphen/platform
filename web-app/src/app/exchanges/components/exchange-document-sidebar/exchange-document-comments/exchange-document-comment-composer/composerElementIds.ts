export const composerElementId = (idPrefix: string | undefined, suffix: string, fallback: string): string =>
    idPrefix ? `${idPrefix}-${suffix}` : fallback;
