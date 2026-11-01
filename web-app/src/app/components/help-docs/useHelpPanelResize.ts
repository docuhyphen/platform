import React, {useCallback, useEffect, useRef, useState} from "react";
import {clampHelpPanelWidth, DEFAULT_HELP_PANEL_WIDTH} from "./helpPanelWidth.ts";

export const useHelpPanelResize = () =>
{
    const [panelWidth, setPanelWidth] = useState(DEFAULT_HELP_PANEL_WIDTH);
    const dragStartRef = useRef<{ startX: number; startWidth: number } | null>(null);
    const spacerRef = useRef<HTMLDivElement | null>(null);
    const panelRef = useRef<HTMLElement | null>(null);

    const onResizePointerMove = useCallback((event: PointerEvent) =>
    {
        if (!dragStartRef.current) return;
        const deltaX = dragStartRef.current.startX - event.clientX;
        setPanelWidth(clampHelpPanelWidth(dragStartRef.current.startWidth + deltaX));
    }, []);

    const stopResize = useCallback(() =>
    {
        dragStartRef.current = null;
        window.removeEventListener("pointermove", onResizePointerMove);
        window.removeEventListener("pointerup", stopResize);
    }, [onResizePointerMove]);

    const onResizePointerDown = useCallback((event: React.PointerEvent<HTMLDivElement>) =>
    {
        event.preventDefault();
        dragStartRef.current = {startX: event.clientX, startWidth: panelWidth};
        window.addEventListener("pointermove", onResizePointerMove);
        window.addEventListener("pointerup", stopResize);
    }, [onResizePointerMove, panelWidth, stopResize]);

    useEffect(() =>
    {
        const onWindowResize = () => setPanelWidth((width) => clampHelpPanelWidth(width));
        window.addEventListener("resize", onWindowResize);
        return () =>
        {
            window.removeEventListener("resize", onWindowResize);
            stopResize();
        };
    }, [stopResize]);

    useEffect(() =>
    {
        const width = `${panelWidth}px`;
        spacerRef.current?.style.setProperty("--help-doc-panel-width", width);
        panelRef.current?.style.setProperty("--help-doc-panel-width", width);
    }, [panelWidth]);

    return {spacerRef, panelRef, onResizePointerDown};
};
