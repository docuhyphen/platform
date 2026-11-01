import {useCallback, useEffect, useRef, useState} from "react";
import {ReactFlowInstance} from "@xyflow/react";

export function useWorkflowGraphCanvasView()
{
    const canvasContainerRef = useRef<HTMLDivElement | null>(null);
    const reactFlowInstanceRef = useRef<ReactFlowInstance | null>(null);
    const [isEnlarged, setIsEnlarged] = useState(false);
    const [isFullscreen, setIsFullscreen] = useState(false);

    const scheduleFitView = useCallback(() =>
    {
        requestAnimationFrame(() =>
        {
            requestAnimationFrame(() =>
            {
                reactFlowInstanceRef.current?.fitView();
            });
        });
    }, []);

    useEffect(() =>
    {
        const handleFullscreenChange = () =>
        {
            const nowFullscreen = document.fullscreenElement === canvasContainerRef.current;
            setIsFullscreen(nowFullscreen);
            scheduleFitView();
        };
        document.addEventListener("fullscreenchange", handleFullscreenChange);
        return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
    }, [scheduleFitView]);

    const toggleFullscreen = useCallback(async () =>
    {
        try
        {
            if (document.fullscreenElement)
            {
                await document.exitFullscreen();
            }
            else if (canvasContainerRef.current)
            {
                await canvasContainerRef.current.requestFullscreen();
            }
        }
        catch (err)
        {
            console.warn("Fullscreen request failed:", err);
        }
    }, []);

    const toggleEnlarged = useCallback(() =>
    {
        if (isEnlarged)
        {
            if (document.fullscreenElement === canvasContainerRef.current)
            {
                document.exitFullscreen().catch(() => undefined);
            }
            setIsEnlarged(false);
            scheduleFitView();
            return;
        }

        setIsEnlarged(true);
        scheduleFitView();
    }, [isEnlarged, scheduleFitView]);

    const registerInstance = useCallback((instance: ReactFlowInstance) =>
    {
        reactFlowInstanceRef.current = instance;
    }, []);

    return {
        canvasContainerRef,
        isEnlarged,
        isFullscreen,
        scheduleFitView,
        toggleFullscreen,
        toggleEnlarged,
        registerInstance,
    };
}
