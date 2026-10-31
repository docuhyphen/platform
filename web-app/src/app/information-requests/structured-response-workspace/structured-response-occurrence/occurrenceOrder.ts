export const movedOccurrenceOrder = (occurrenceIds: string[], occurrenceId: string, offset: -1 | 1): string[] => {
    const from = occurrenceIds.indexOf(occurrenceId);
    const to = from + offset;
    if (from < 0 || to < 0 || to >= occurrenceIds.length) return occurrenceIds;
    const next = [...occurrenceIds];
    next[from] = occurrenceIds[to];
    next[to] = occurrenceId;
    return next;
};
