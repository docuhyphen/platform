/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import SettingsLeaveDialog from "./SettingsLeaveDialog.tsx";
import {SettingsLeaveGuardContext, useUnsavedSettingsWork} from "./settingsLeaveGuard.ts";
import {useSettingsTabSelection} from "./useSettingsTabSelection.ts";

const UnsavedWork = ({unsaved}: { unsaved: boolean }): null => {
    useUnsavedSettingsWork(unsaved);
    return null;
};

const SettingsView = ({unsaved}: { unsaved: boolean }) => {
    const tabs = useSettingsTabSelection("profile");
    return (
        <SettingsLeaveGuardContext.Provider value={tabs.guard}>
            {tabs.selectedValue === "profile" && <UnsavedWork unsaved={unsaved}/>}
            <output id={"selected-tab"}>{String(tabs.selectedValue)}</output>
            <button id={"open-templates"}
                    onClick={() => tabs.select("templates")}>
                Open templates
            </button>
            <SettingsLeaveDialog open={tabs.pendingTab !== null}
                                 onLeave={tabs.leave}
                                 onStay={tabs.stay}/>
        </SettingsLeaveGuardContext.Provider>
    );
};

const selected = () => document.querySelector("#selected-tab")?.textContent;

describe("leaving a Settings tab that holds unsaved work", () => {
    afterEach(cleanup);

    it("asks first, and stays on the tab when the person keeps editing", async () => {
        render(<SettingsView unsaved={true}/>);

        fireEvent.click(screen.getByRole("button", {name: "Open templates"}));
        expect(within(screen.getByRole("dialog")).getByText("Leave without saving?")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Keep editing"}));

        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(selected()).toBe("profile");
    });

    it("leaves the tab when the person discards the unsaved work", () => {
        render(<SettingsView unsaved={true}/>);

        fireEvent.click(screen.getByRole("button", {name: "Open templates"}));
        fireEvent.click(screen.getByRole("button", {name: "Discard changes"}));

        expect(selected()).toBe("templates");
    });

    it("switches at once when nothing is unsaved", () => {
        render(<SettingsView unsaved={false}/>);

        fireEvent.click(screen.getByRole("button", {name: "Open templates"}));

        expect(selected()).toBe("templates");
        expect(screen.queryByRole("dialog")).toBeNull();
    });
});
