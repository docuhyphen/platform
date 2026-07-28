/** @vitest-environment jsdom */
import {cleanup, render, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import DocumentLibraryEditorDialog from "./DocumentLibraryEditorDialog.tsx";

const getAvailableVariables = vi.fn();

vi.mock("../../../services/variableService.ts", () => ({
    getAvailableVariables: (...args: unknown[]) => getAvailableVariables(...args),
}));

vi.mock("../../../services/platformDocumentLibraryService.ts", () => ({
    createPlatformDocument: vi.fn(),
    updatePlatformDocument: vi.fn(),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("DocumentLibraryEditorDialog", () =>
{
    it("does not load tenant variables in enforced APP mode", async () =>
    {
        render(
            <DocumentLibraryEditorDialog
                open={true}
                scope={"APP"}
                enforcedScope={"APP"}
                onClose={vi.fn()}
                onSaved={vi.fn()}/>,
        );

        await waitFor(() =>
            expect(document.querySelector("#doc-editor-title")).toBeTruthy(),
        );
        expect(getAvailableVariables).not.toHaveBeenCalled();
    });
});
