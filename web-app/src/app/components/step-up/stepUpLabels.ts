export const friendlyStepUpActionLabel = (action?: string | null): string =>
{
    if (!action) return "this action";
    const cleaned = action.replace(/^ORG_/, "").replace(/_/g, " ").toLowerCase();
    return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
};
