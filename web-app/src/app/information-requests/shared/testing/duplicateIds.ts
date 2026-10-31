export const duplicateIds = (root: ParentNode): string[] => {
    const counts = new Map<string, number>();
    root.querySelectorAll("[id]").forEach(element => {
        const id = element.getAttribute("id") ?? "";
        counts.set(id, (counts.get(id) ?? 0) + 1);
    });
    return Array.from(counts)
        .filter(([, count]) => count > 1)
        .map(([id]) => id)
        .sort();
};
