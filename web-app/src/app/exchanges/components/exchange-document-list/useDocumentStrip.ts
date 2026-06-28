import React from "react";

const CARD_SCROLL_DISTANCE = 376;

export const useDocumentStrip = (selectedDocumentId: string | undefined, documentCount: number) => {
    const stripRef = React.useRef<HTMLDivElement>(null);
    const [canScrollLeft, setCanScrollLeft] = React.useState(false);
    const [canScrollRight, setCanScrollRight] = React.useState(false);

    const updateScrollState = React.useCallback(() => {
        const strip = stripRef.current;
        if (!strip) return;
        setCanScrollLeft(strip.scrollLeft > 1);
        setCanScrollRight(strip.scrollLeft + strip.clientWidth < strip.scrollWidth - 1);
    }, []);

    React.useEffect(() => {
        const strip = stripRef.current;
        if (!strip) return;
        updateScrollState();
        const observer = typeof ResizeObserver === "undefined" ? undefined : new ResizeObserver(updateScrollState);
        observer?.observe(strip);
        strip.addEventListener("scroll", updateScrollState, {passive: true});
        return () => {
            observer?.disconnect();
            strip.removeEventListener("scroll", updateScrollState);
        };
    }, [documentCount, updateScrollState]);

    React.useEffect(() => {
        if (!selectedDocumentId) return;
        const card = document.getElementById(`exchange-document-card-${selectedDocumentId}`);
        card?.scrollIntoView({behavior: "smooth", block: "nearest", inline: "nearest"});
    }, [selectedDocumentId]);

    const scrollByCard = (direction: -1 | 1) => {
        stripRef.current?.scrollBy({left: direction * CARD_SCROLL_DISTANCE, behavior: "smooth"});
    };
    const onStripKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (event.target !== event.currentTarget) return;
        if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
            event.preventDefault();
            scrollByCard(event.key === "ArrowLeft" ? -1 : 1);
        }
    };

    return {stripRef, canScrollLeft, canScrollRight, scrollByCard, onStripKeyDown};
};
