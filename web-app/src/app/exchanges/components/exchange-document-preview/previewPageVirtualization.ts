export const DEFAULT_PAGE_WIDTH = 612;
export const DEFAULT_PAGE_HEIGHT = 792;
export const VIRTUALIZATION_BUFFER_PAGES = 2;
export const COMPACT_CONTROLS_WIDTH_THRESHOLD = 560;
export const PROGRAMMATIC_SCROLL_SETTLE_MS = 450;
export const ENLARGE_CLOSE_ANIMATION_MS = 220;

export interface PageDimensions
{
    width: number;
    height: number;
}

export type PagePresentationProps = { width: number | undefined } | { scale: number };

export const addPagesAround = (pages: Set<number>, pageNumber: number, numPages: number): boolean =>
{
    let mutated = false;
    for (let p = Math.max(1, pageNumber - VIRTUALIZATION_BUFFER_PAGES);
         p <= Math.min(numPages, pageNumber + VIRTUALIZATION_BUFFER_PAGES);
         p++)
    {
        if (!pages.has(p))
        {
            pages.add(p);
            mutated = true;
        }
    }
    return mutated;
};

export const withPagesAround = (pages: Set<number>, pageNumber: number, numPages: number): Set<number> =>
{
    const next = new Set(pages);
    addPagesAround(next, pageNumber, numPages);
    return next;
};

export const isFarFromPage = (pageNumber: number, currentPage: number): boolean =>
{
    return Math.abs(pageNumber - currentPage) > VIRTUALIZATION_BUFFER_PAGES + 2;
};
