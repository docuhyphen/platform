/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformCommunications from "./PlatformCommunications.tsx";

const listPlatformCommunications = vi.fn();
const editorProps = vi.fn();

vi.mock("../../../services/platformCommunicationService.ts", () => ({
    listPlatformCommunications: (...args: unknown[]) => listPlatformCommunications(...args),
    getPlatformCommunication: vi.fn(),
    setPlatformCommunicationActive: vi.fn(),
    setPlatformCommunicationPublished: vi.fn(),
    deletePlatformCommunication: vi.fn(),
}));

vi.mock("../../settings/communications-tab/CommunicationEditorDialog.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        editorProps(props);
        return props.open ? <div id={"test-platform-communication-editor"}/> : null;
    },
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformCommunications", () =>
{
    it("opens its editor with enforced PLATFORM scope", async () =>
    {
        listPlatformCommunications.mockResolvedValue([]);
        render(<PlatformCommunications/>);

        await waitFor(() => expect(listPlatformCommunications).toHaveBeenCalledTimes(1));
        expect(screen.queryByText(
            "Manage PLATFORM-scoped communications without loading tenant variable data.",
        )).toBeNull();
        expect(document.querySelector("#platform-communication-create svg")).toBeTruthy();
        expect(document.querySelector(
            "#platform-communications-scrollable-content #platform-communication-create",
        )).toBeNull();
        fireEvent.click(screen.getByText("Create platform communication"));

        expect(document.querySelector("#test-platform-communication-editor")).toBeTruthy();
        expect(editorProps).toHaveBeenLastCalledWith(expect.objectContaining({
            open: true,
            scope: "PLATFORM",
            enforcedScope: "PLATFORM",
            createAsTemplate: true,
        }));
    });
});
