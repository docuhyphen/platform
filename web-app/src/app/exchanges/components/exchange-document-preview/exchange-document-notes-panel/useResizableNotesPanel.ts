import {KeyboardEvent, PointerEvent, useCallback, useLayoutEffect, useRef} from "react";

const PANEL_WIDTH_STORAGE_KEY = "exchanges.preview.notes.width";
const DEFAULT_PANEL_WIDTH = 360;
const MIN_PANEL_WIDTH = 280;
const MAX_PANEL_WIDTH = 640;
const KEYBOARD_RESIZE_STEP = 24;

const clampWidth = (width: number) =>
    Math.min(Math.max(width, MIN_PANEL_WIDTH), Math.min(MAX_PANEL_WIDTH, window.innerWidth * 0.5));

export const useResizableNotesPanel = () =>
{
    const panelRef = useRef<HTMLElement>(null);

    const setPanelWidth = useCallback((width: number) =>
    {
        const nextWidth = clampWidth(width);
        panelRef.current?.style.setProperty("--document-notes-panel-width", `${nextWidth}px`);
        window.localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(nextWidth));
    }, []);

    useLayoutEffect(() =>
    {
        const savedWidth = Number(window.localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
        setPanelWidth(Number.isFinite(savedWidth) && savedWidth > 0 ? savedWidth : DEFAULT_PANEL_WIDTH);
    }, [setPanelWidth]);

    const handleResizeStart = (event: PointerEvent<HTMLDivElement>) =>
    {
        const panel = panelRef.current;
        if (!panel) return;
        event.currentTarget.setPointerCapture(event.pointerId);
        const startX = event.clientX;
        const startWidth = panel.getBoundingClientRect().width;

        const handlePointerMove = (moveEvent: globalThis.PointerEvent) =>
        {
            setPanelWidth(startWidth + startX - moveEvent.clientX);
        };
        const handlePointerUp = () =>
        {
            window.removeEventListener("pointermove", handlePointerMove);
            window.removeEventListener("pointerup", handlePointerUp);
        };
        window.addEventListener("pointermove", handlePointerMove);
        window.addEventListener("pointerup", handlePointerUp);
    };

    const handleResizeKeyDown = (event: KeyboardEvent<HTMLDivElement>) =>
    {
        const measuredWidth = panelRef.current?.getBoundingClientRect().width ?? 0;
        const savedWidth = Number(window.localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
        const currentWidth = measuredWidth > 0
            ? measuredWidth
            : Number.isFinite(savedWidth) && savedWidth > 0 ? savedWidth : DEFAULT_PANEL_WIDTH;
        if (event.key === "ArrowLeft")
        {
            event.preventDefault();
            setPanelWidth(currentWidth + KEYBOARD_RESIZE_STEP);
        }
        if (event.key === "ArrowRight")
        {
            event.preventDefault();
            setPanelWidth(currentWidth - KEYBOARD_RESIZE_STEP);
        }
    };

    return {panelRef, handleResizeStart, handleResizeKeyDown};
};
