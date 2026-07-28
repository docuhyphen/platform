/** @vitest-environment jsdom */
import {cleanup, render} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformDocumentDialogs from "./PlatformDocumentDialogs.tsx";

const editorProps = vi.fn();

vi.mock("../../settings/document-library-tab/DocumentLibraryEditorDialog.tsx", () => ({
    default: (props: Record<string, unknown>) =>
    {
        editorProps(props);
        return null;
    },
}));

vi.mock("../../settings/document-library-tab/DocumentLibraryUploadDialog.tsx", () => ({
    default: () => null,
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("PlatformDocumentDialogs", () =>
{
    it("enforces APP scope in the shared editor", () =>
    {
        render(
            <PlatformDocumentDialogs
                editing={"new"}
                uploading={null}
                deleting={null}
                deletePending={false}
                onCloseEditor={vi.fn()}
                onCloseUpload={vi.fn()}
                onCloseDelete={vi.fn()}
                onSaved={vi.fn()}
                onConfirmDelete={vi.fn()}/>,
        );

        expect(editorProps).toHaveBeenCalledWith(expect.objectContaining({
            open: true,
            scope: "APP",
            enforcedScope: "APP",
        }));
    });
});
