import {useCallback, useMemo, useState} from "react";
import {TabValue} from "@fluentui/react-components";
import {SettingsPageTransitionDirection} from "../components/settings-page-transition/SettingsPageTransition.tsx";
import {settingsTabOrder} from "../settingsTabs.ts";
import {SettingsLeaveGuard} from "./settingsLeaveGuard.ts";

const directionBetween = (current: TabValue, next: TabValue): SettingsPageTransitionDirection => {
    if (next === current) return null;
    const currentIndex = settingsTabOrder.indexOf(current as string);
    const nextIndex = settingsTabOrder.indexOf(next as string);
    return currentIndex >= 0 && nextIndex >= 0 && nextIndex < currentIndex ? "back" : "forward";
};

export const useSettingsTabSelection = (initialTab: TabValue) => {
    const [selectedValue, setSelectedValue] = useState<TabValue>(initialTab);
    const [direction, setDirection] = useState<SettingsPageTransitionDirection>(null);
    const [unsavedOwners, setUnsavedOwners] = useState<ReadonlySet<string>>(new Set());
    const [pendingTab, setPendingTab] = useState<TabValue | null>(null);

    const guard = useMemo<SettingsLeaveGuard>(() => ({
        report: (owner, unsaved) => setUnsavedOwners(current => {
            if (unsaved === current.has(owner)) return current;
            const next = new Set(current);
            if (unsaved) next.add(owner);
            else next.delete(owner);
            return next;
        }),
    }), []);

    const reset = useCallback((tab: TabValue) => {
        setSelectedValue(tab);
        setDirection(null);
    }, []);

    const switchTo = useCallback((next: TabValue) => {
        setDirection(directionBetween(selectedValue, next));
        setSelectedValue(next);
    }, [selectedValue]);

    return {
        selectedValue,
        direction,
        guard,
        pendingTab,
        select: (next: TabValue) => {
            if (next !== selectedValue && unsavedOwners.size > 0) setPendingTab(next);
            else switchTo(next);
        },
        leave: () => {
            if (pendingTab !== null) switchTo(pendingTab);
            setPendingTab(null);
        },
        stay: () => setPendingTab(null),
        reset,
    };
};
