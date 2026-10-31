const KEY_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;

const SEGMENT_PATTERN = /^([a-z0-9](?:[a-z0-9-]*[a-z0-9])?)\[(\d+)]$/;

const ROOT_OCCURRENCE_PATH = "root";

const hexOf = (value: string): string =>
    Array.from({length: value.length}, (_, index) => value.charCodeAt(index).toString(16).padStart(4, "0")).join("");

const pathKeyOrNull = (occurrencePath: string): string | null => {
    if (occurrencePath === ROOT_OCCURRENCE_PATH) return occurrencePath;
    const segments: string[] = [];
    for (const segment of occurrencePath.split("/")) {
        const match = SEGMENT_PATTERN.exec(segment);
        if (!match) return null;
        segments.push(`${match[1]}_${match[2]}`);
    }
    return segments.join("_");
};

export const occurrenceElementKey = (occurrencePath: string): string =>
    pathKeyOrNull(occurrencePath) ?? `_${hexOf(occurrencePath)}`;

export const groupElementKey = (groupKey: string): string =>
    KEY_PATTERN.test(groupKey) ? groupKey : `_${hexOf(groupKey)}`;

export const requirementElementKey = (occurrencePath: string, requirementKey: string): string => {
    const pathKey = pathKeyOrNull(occurrencePath);
    return pathKey !== null && KEY_PATTERN.test(requirementKey)
        ? `${pathKey}-${requirementKey}`
        : `_${hexOf(occurrencePath)}_${hexOf(requirementKey)}`;
};
