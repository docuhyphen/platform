/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import ExchangeInitiationDocumentsTab from "./ExchangeInitiationDocumentsTab.tsx";

vi.mock("../document-library-picker/DocumentLibraryPicker.tsx", () => ({
    default: () => <div id={"test-document-library-picker"}>Document library picker</div>,
}));

const NOTE = "This Blueprint's Information Request collects the documents, so Exchange documents are optional.";

const renderTab = (informationRequestCollectsDocuments: boolean, locked: boolean) =>
    render(
        <ExchangeInitiationDocumentsTab documents={[]}
                                        onDocumentNameChange={vi.fn()}
                                        onDocumentTypeChange={vi.fn()}
                                        onRestrictDocumentTypeChange={vi.fn()}
                                        onDeleteDocument={vi.fn()}
                                        onRequiredChange={vi.fn()}
                                        onUnlink={vi.fn()}
                                        addNewDocument={vi.fn()}
                                        addLibraryDocument={vi.fn()}
                                        locked={locked}
                                        canUseDocumentLibrary={true}
                                        informationRequestCollectsDocuments={informationRequestCollectsDocuments}/>,
    );

describe("ExchangeInitiationDocumentsTab", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    afterEach(cleanup);

    it("tells the author that a locked Blueprint with no document slots can start without documents", () => {
        renderTab(true, true);

        expect(screen.getByText(NOTE)).toBeTruthy();
        expect(document.querySelector("#exchange-information-request-documents-note")).toBeTruthy();
        expect(screen.queryByText("Add Document")).toBeNull();
    });

    it("keeps the document actions beside the note when the Blueprint is not locked", () => {
        renderTab(true, false);

        expect(screen.getByText(NOTE)).toBeTruthy();
        expect(screen.getByText("Add Document")).toBeTruthy();
    });

    it("says nothing about optional documents when the Exchange still needs them", () => {
        renderTab(false, false);

        expect(screen.queryByText(NOTE)).toBeNull();
    });
});
