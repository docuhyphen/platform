import {Mock, vi} from "vitest";

export interface PreviewTabDouble {
    opener: unknown;
    location: { replace: Mock<(url: string) => void> };
    close: Mock<() => void>;
}

export const previewTabDouble = (): PreviewTabDouble => ({
    opener: {},
    location: {replace: vi.fn<(url: string) => void>()},
    close: vi.fn<() => void>(),
});

export const openingPreviewTab = (tab: PreviewTabDouble | null) =>
    vi.spyOn(window, "open").mockImplementation(() => tab as unknown as Window | null);
