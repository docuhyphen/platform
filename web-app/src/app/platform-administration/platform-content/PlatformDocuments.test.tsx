/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformDocuments from "./PlatformDocuments.tsx";

const listPlatformDocuments = vi.fn();
const editorProps = vi.fn();

vi.mock("../../../services/platformDocumentLibraryService.ts", () => ({
    listPlatformDocuments: (...args: unknown[]) => listPlatformDocuments(...args),
    deletePlatformDocument: vi.fn(),
    downloadPlatformDocumentFile: vi.fn(),
    setPlatformDocumentActive: vi.fn(),
    setPlatformDocumentPublished: vi.fn(),
}));

vi.mock("./PlatformDocumentDialogs.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        editorProps(props);
        return props.editing ? <div id={"test-platform-document-editor"}/> : null;
    },
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformDocuments", () =>
{
    it("opens its editor with enforced APP scope", async () =>
    {
        listPlatformDocuments.mockResolvedValue([]);
        render(<PlatformDocuments/>);

        await waitFor(() => expect(listPlatformDocuments).toHaveBeenCalledTimes(1));
        expect(screen.queryByText(
            "Manage APP-scoped library documents without loading organization or personal content.",
        )).toBeNull();
        expect(document.querySelector("#platform-document-create svg")).toBeTruthy();
        expect(document.querySelector(
            "#platform-documents-scrollable-content #platform-document-create",
        )).toBeNull();
        fireEvent.click(screen.getByText("Create platform document"));

        expect(document.querySelector("#test-platform-document-editor")).toBeTruthy();
        expect(editorProps).toHaveBeenLastCalledWith(expect.objectContaining({
            editing: "new",
        }));
    });
});
