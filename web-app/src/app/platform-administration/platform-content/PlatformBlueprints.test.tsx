/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformBlueprints from "./PlatformBlueprints.tsx";

const listPlatformBlueprints = vi.fn();
const editorProps = vi.fn();

vi.mock("../../../services/platformBlueprintService.ts", () => ({
    listPlatformBlueprints: (...args: unknown[]) => listPlatformBlueprints(...args),
    setPlatformBlueprintActive: vi.fn(),
    setPlatformBlueprintPublished: vi.fn(),
    deletePlatformBlueprint: vi.fn(),
}));

vi.mock("../../settings/blueprints-tab/BlueprintEditorDialog.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        editorProps(props);
        return props.open ? <div id={"test-platform-blueprint-editor"}/> : null;
    },
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformBlueprints", () =>
{
    it("opens its editor with enforced APP scope", async () =>
    {
        listPlatformBlueprints.mockResolvedValue([]);
        render(<PlatformBlueprints/>);

        await waitFor(() => expect(listPlatformBlueprints).toHaveBeenCalledTimes(1));
        expect(screen.queryByText(
            "Manage APP-scoped Blueprints without loading organization or personal content.",
        )).toBeNull();
        expect(document.querySelector("#platform-blueprint-create svg")).toBeTruthy();
        expect(document.querySelector(
            "#platform-blueprints-scrollable-content #platform-blueprint-create",
        )).toBeNull();
        fireEvent.click(screen.getByText("Create platform Blueprint"));

        expect(document.querySelector("#test-platform-blueprint-editor")).toBeTruthy();
        expect(editorProps).toHaveBeenLastCalledWith(expect.objectContaining({
            open: true,
            scope: "APP",
            enforcedScope: "APP",
        }));
    });
});
