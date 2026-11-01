import {useEffect, useRef, useState} from 'react';
import {ENLARGE_CLOSE_ANIMATION_MS} from './previewPageVirtualization.ts';

export const usePreviewEnlargement = (scrollCurrentPageIntoView: () => void) =>
{
    const sectionRef = useRef<HTMLElement>(null);
    const [isEnlarged, setIsEnlarged] = useState<boolean>(false);
    const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
    const [isClosingEnlarged, setIsClosingEnlarged] = useState<boolean>(false);
    const [isNotesPanelOpen, setIsNotesPanelOpen] = useState<boolean>(false);
    const closeAnimationTimeoutRef = useRef<number | null>(null);
    const fullscreenStartedInlineRef = useRef(false);

    useEffect(() =>
    {
        return () =>
        {
            if (closeAnimationTimeoutRef.current)
            {
                window.clearTimeout(closeAnimationTimeoutRef.current);
            }
        };
    }, []);

    useEffect(() =>
    {
        const handleFullscreenChange = () =>
        {
            const active = window.document.fullscreenElement === sectionRef.current;
            setIsFullscreen(active);
            if (active)
            {
                setIsEnlarged(true);
                return;
            }

            if (fullscreenStartedInlineRef.current)
            {
                fullscreenStartedInlineRef.current = false;
                setIsEnlarged(false);
                setIsNotesPanelOpen(false);
            }
        };
        window.document.addEventListener('fullscreenchange', handleFullscreenChange);
        return () =>
        {
            window.document.removeEventListener('fullscreenchange', handleFullscreenChange);
        };
    }, []);

    const toggleNotesPanel = () =>
    {
        setIsNotesPanelOpen(currentValue => !currentValue);
    };

    const closeNotesPanel = () =>
    {
        setIsNotesPanelOpen(false);
    };

    const toggleEnlarge = () =>
    {
        if (isEnlarged)
        {
            if (isClosingEnlarged) return;
            if (window.document.fullscreenElement)
            {
                window.document.exitFullscreen().catch(() => undefined);
            }
            setIsClosingEnlarged(true);
            closeAnimationTimeoutRef.current = window.setTimeout(() =>
            {
                setIsEnlarged(false);
                setIsClosingEnlarged(false);
                setIsNotesPanelOpen(false);
                window.requestAnimationFrame(scrollCurrentPageIntoView);
            }, ENLARGE_CLOSE_ANIMATION_MS);
            return;
        }

        setIsEnlarged(true);
        window.requestAnimationFrame(() =>
        {
            window.requestAnimationFrame(scrollCurrentPageIntoView);
        });
    };

    const toggleFullscreen = async () =>
    {
        const root = sectionRef.current;
        if (!root) return;

        try
        {
            if (window.document.fullscreenElement)
            {
                await window.document.exitFullscreen();
            }
            else
            {
                const startedInline = !isEnlarged;
                fullscreenStartedInlineRef.current = startedInline;
                if (startedInline)
                {
                    setIsEnlarged(true);
                }
                await root.requestFullscreen();
            }
        }
        catch (err)
        {
            if (fullscreenStartedInlineRef.current)
            {
                fullscreenStartedInlineRef.current = false;
                setIsEnlarged(false);
            }
            console.warn('Fullscreen request failed:', err);
        }
    };

    return {
        sectionRef,
        isEnlarged,
        isFullscreen,
        isClosingEnlarged,
        isNotesPanelOpen,
        toggleNotesPanel,
        closeNotesPanel,
        toggleEnlarge,
        toggleFullscreen,
    };
};

export type PreviewEnlargement = ReturnType<typeof usePreviewEnlargement>;
