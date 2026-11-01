import React, {useCallback, useEffect, useRef, useState} from 'react';
import {addPagesAround, isFarFromPage, withPagesAround} from './previewPageVirtualization.ts';

export const usePreviewPageVirtualization = (
    pdfContainerRef: React.RefObject<HTMLDivElement>,
    numPages: number,
    currentPage: number,
    pdfUrl: string | null,
) =>
{
    const pageElementsRef = useRef<Map<number, HTMLDivElement>>(new Map());
    const intersectionObserverRef = useRef<IntersectionObserver | null>(null);
    const [visiblePages, setVisiblePages] = useState<Set<number>>(() => new Set([1]));

    useEffect(() =>
    {
        return () =>
        {
            intersectionObserverRef.current?.disconnect();
        };
    }, []);

    useEffect(() =>
    {
        intersectionObserverRef.current?.disconnect();

        const root = pdfContainerRef.current;
        if (!root || numPages === 0)
        {
            return;
        }

        const observer = new IntersectionObserver(
            (entries) =>
            {
                setVisiblePages((previous) =>
                {
                    const next = new Set(previous);
                    let mutated = false;
                    for (const entry of entries)
                    {
                        const target = entry.target as HTMLElement;
                        const pageNumber = Number(target.getAttribute('data-page-number'));
                        if (!pageNumber) continue;

                        if (entry.isIntersecting)
                        {
                            mutated = addPagesAround(next, pageNumber, numPages) || mutated;
                        }
                        else if (next.has(pageNumber) && isFarFromPage(pageNumber, currentPage))
                        {
                            next.delete(pageNumber);
                            mutated = true;
                        }
                    }
                    return mutated ? next : previous;
                });
            },
            {
                root,
                rootMargin: '200% 0px 200% 0px',
                threshold: 0,
            }
        );

        intersectionObserverRef.current = observer;
        pageElementsRef.current.forEach((el) => observer.observe(el));

        return () =>
        {
            observer.disconnect();
        };
    }, [pdfContainerRef, numPages, pdfUrl, currentPage]);

    const registerPageRef = useCallback((pageNumber: number, element: HTMLDivElement | null) =>
    {
        const existing = pageElementsRef.current.get(pageNumber);
        if (existing && existing !== element)
        {
            intersectionObserverRef.current?.unobserve(existing);
            pageElementsRef.current.delete(pageNumber);
        }
        if (element)
        {
            pageElementsRef.current.set(pageNumber, element);
            intersectionObserverRef.current?.observe(element);
        }
    }, []);

    const revealPagesAround = useCallback((pageNumber: number, pageCount: number) =>
    {
        setVisiblePages((previous) => withPagesAround(previous, pageNumber, pageCount));
    }, []);

    const resetVisiblePages = useCallback(() =>
    {
        setVisiblePages(new Set([1]));
    }, []);

    return {visiblePages, pageElementsRef, registerPageRef, revealPagesAround, resetVisiblePages};
};
