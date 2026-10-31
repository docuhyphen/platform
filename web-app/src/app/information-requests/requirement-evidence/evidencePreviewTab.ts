export interface EvidencePreviewTab {
    show: (url: string) => void;
    close: () => void;
}

export const PREVIEW_BLOCKED_MESSAGE = "Your browser blocked the preview tab. Allow pop-ups for this site, or select Download.";

export const openEvidencePreviewTab = (): EvidencePreviewTab | null => {
    const tab = window.open("", "_blank");
    if (!tab) return null;
    tab.opener = null;
    return {
        show: url => tab.location.replace(url),
        close: () => tab.close(),
    };
};
