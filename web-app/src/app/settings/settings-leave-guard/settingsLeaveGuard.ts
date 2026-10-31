import {createContext, useContext, useEffect, useId} from "react";

export interface SettingsLeaveGuard {
    report: (owner: string, unsaved: boolean) => void;
}

export const SettingsLeaveGuardContext = createContext<SettingsLeaveGuard | null>(null);

export const useUnsavedSettingsWork = (unsaved: boolean) => {
    const guard = useContext(SettingsLeaveGuardContext);
    const owner = useId();

    useEffect(() => {
        guard?.report(owner, unsaved);
        return () => guard?.report(owner, false);
    }, [guard, owner, unsaved]);

    useEffect(() => {
        if (!unsaved) return;
        const warn = (event: BeforeUnloadEvent) => event.preventDefault();
        window.addEventListener("beforeunload", warn);
        return () => window.removeEventListener("beforeunload", warn);
    }, [unsaved]);
};
