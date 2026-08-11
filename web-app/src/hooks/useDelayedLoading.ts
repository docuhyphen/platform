import {useEffect, useRef, useState} from "react";

export const useDelayedLoading = (
    loading: boolean,
    delayMs = 200,
    minVisibleMs = 300,
): boolean =>
{
    const [visible, setVisible] = useState(false);
    const shownAtRef = useRef<number | null>(null);

    useEffect(() =>
    {
        let timer: ReturnType<typeof setTimeout> | undefined;

        if (loading && !visible)
        {
            timer = setTimeout(() =>
            {
                shownAtRef.current = Date.now();
                setVisible(true);
            }, delayMs);
        }
        else if (loading)
        {
            return undefined;
        }
        else if (visible && shownAtRef.current)
        {
            const elapsedMs = Date.now() - shownAtRef.current;
            timer = setTimeout(() =>
            {
                shownAtRef.current = null;
                setVisible(false);
            }, Math.max(minVisibleMs - elapsedMs, 0));
        }
        else
        {
            shownAtRef.current = null;
            setVisible(false);
        }

        return () =>
        {
            if (timer) clearTimeout(timer);
        };
    }, [delayMs, loading, minVisibleMs, visible]);

    return visible;
};
