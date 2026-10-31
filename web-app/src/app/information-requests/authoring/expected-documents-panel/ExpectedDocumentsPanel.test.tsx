/** @vitest-environment jsdom */
import {cleanup, render, screen, within} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import ExpectedDocumentsPanel from "./ExpectedDocumentsPanel.tsx";

describe("ExpectedDocumentsPanel", () => {
    afterEach(cleanup);

    it("names each expected document with its requiredness, type restriction, and library snapshot", () => {
        render(<ExpectedDocumentsPanel placeholders={[
            {
                id: "placeholder-a",
                title: "Signed record",
                required: true,
                restrictType: true,
                restrictedType: "PDF",
                libraryTitle: "Record template",
                libraryFileName: "record-template.pdf",
                libraryFileSizeBytes: 2048,
                displayOrder: 0,
            },
            {
                id: "placeholder-b",
                title: "Supporting statement",
                required: false,
                restrictType: false,
                restrictedType: "DOCX",
                displayOrder: 1,
            },
        ]}/>);

        const panel = screen.getByRole("region", {name: "Expected documents"});
        const first = within(panel).getByText("Signed record").closest("li") as HTMLElement;
        expect(within(first).getByText("Required")).toBeTruthy();
        expect(within(first).getByText("PDF only")).toBeTruthy();
        expect(within(first).getByText("From the Document Library: Record template, record-template.pdf, 2 KiB")).toBeTruthy();
        const second = within(panel).getByText("Supporting statement").closest("li") as HTMLElement;
        expect(within(second).getByText("Optional")).toBeTruthy();
        expect(within(second).queryByText("DOCX only")).toBeNull();
        expect(within(second).queryByText(/Document Library/)).toBeNull();
    });

    it("leaves out a library file size the server sends as null", () => {
        render(<ExpectedDocumentsPanel placeholders={[{
            id: "placeholder-c",
            title: "Reference record",
            required: true,
            restrictType: false,
            restrictedType: null,
            libraryTitle: "Reference template",
            libraryFileName: null,
            libraryFileSizeBytes: null,
            displayOrder: 0,
        }]}/>);

        expect(screen.getByText("From the Document Library: Reference template")).toBeTruthy();
    });

    it("renders nothing for a request created without expected documents", () => {
        const {container} = render(<ExpectedDocumentsPanel placeholders={[]}/>);

        expect(container.childElementCount).toBe(0);
    });
});
