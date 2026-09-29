import {useEffect, useRef} from "react";

export const AUTOSAVE_DELAY_MS = 2000;

interface AutosaveInput
{
    signature: string;
    enabled: boolean;
    save: () => void;
}

export const useResponseAutosave = ({signature, enabled, save}: AutosaveInput) =>
{
    const latestSave = useRef(save);

    useEffect(() =>
    {
        latestSave.current = save;
    }, [save]);

    useEffect(() =>
    {
        if (!enabled || !signature) return;
        const timer = window.setTimeout(() => latestSave.current(), AUTOSAVE_DELAY_MS);
        return () => window.clearTimeout(timer);
    }, [enabled, signature]);
};
