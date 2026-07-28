/** @vitest-environment jsdom */
import {cleanup, render, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import DocumentLibraryPicker from "./DocumentLibraryPicker.tsx";

const listDocumentLibraryEntries = vi.fn();

vi.mock("../../../../services/documentLibraryService.ts", () => ({
    listDocumentLibraryEntries: (...args: unknown[]) => listDocumentLibraryEntries(...args),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("DocumentLibraryPicker", () =>
{
    it("requests only APP documents when its scope is enforced", async () =>
    {
        listDocumentLibraryEntries.mockResolvedValue([]);

        const {queryByText} = render(
            <DocumentLibraryPicker
                enforcedScope={"APP"}
                onSelect={vi.fn()}
                onBack={vi.fn()}/>,
        );

        await waitFor(() =>
            expect(listDocumentLibraryEntries).toHaveBeenCalledWith({scope: "APP"}),
        );
        expect(queryByText("My Documents")).toBeNull();
        expect(queryByText("Organization")).toBeNull();
        expect(listDocumentLibraryEntries).toHaveBeenCalledTimes(1);
    });
});
