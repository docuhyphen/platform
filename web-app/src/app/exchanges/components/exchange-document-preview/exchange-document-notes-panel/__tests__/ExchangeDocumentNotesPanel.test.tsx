/** @vitest-environment jsdom */
import {cleanup, fireEvent, render} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {DocumentDetailedDto} from "../../../../../models/models.tsx";
import ExchangeDocumentNotesPanel from "../ExchangeDocumentNotesPanel.tsx";

vi.mock(
    "../../../exchange-document-sidebar/exchange-document-comments/ExchangeDocumentComments.tsx",
    () => ({
        default: ({idPrefix}: {idPrefix: string}) => (
            <div id={`${idPrefix}-comments-stub`}/>
        ),
    })
);

const documentFixture = {
    id: "document-1",
    title: "Agreement",
} as DocumentDetailedDto;

afterEach(cleanup);

describe("ExchangeDocumentNotesPanel", () =>
{
    it("renders preview-scoped comments and closes from the panel header", () =>
    {
        const onClose = vi.fn();

        render(
            <ExchangeDocumentNotesPanel
                exchangeId={"exchange-1"}
                exchangeDocument={documentFixture}
                onClose={onClose}
                pageNumber={3}
                documentVersionId={"version-1"}
                onNavigateToPage={vi.fn()}
            />
        );

        expect(document.getElementById("exchange-document-preview-comments-stub")).toBeTruthy();

        fireEvent.click(document.getElementById("exchange-document-preview-notes-close") as HTMLElement);

        expect(onClose).toHaveBeenCalledOnce();
    });

    it("resizes with the keyboard and remembers the preferred width", () =>
    {
        render(
            <ExchangeDocumentNotesPanel
                exchangeId={"exchange-1"}
                exchangeDocument={documentFixture}
                onClose={vi.fn()}
                pageNumber={1}
                onNavigateToPage={vi.fn()}
            />
        );

        fireEvent.keyDown(
            document.getElementById("exchange-document-preview-notes-resize-handle") as HTMLElement,
            {key: "ArrowLeft"}
        );

        expect(window.localStorage.getItem("exchanges.preview.notes.width")).toBe("384");
    });
});
