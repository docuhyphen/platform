import {describe, expect, it} from "vitest";
import {DocumentDetailedDto} from "../models/models.tsx";
import {resolvePreviewDocumentSelection} from "./exchangeDocumentSelection.ts";

const documents = [
    {id: "first", title: "First"},
    {id: "second", title: "Second"},
] as DocumentDetailedDto[];

describe("resolvePreviewDocumentSelection", () =>
{
    it("selects the first document when automatic preview is enabled", () =>
    {
        expect(resolvePreviewDocumentSelection(documents, undefined, true)?.id).toBe("first");
    });

    it("does not select a document when automatic preview is disabled", () =>
    {
        expect(resolvePreviewDocumentSelection(documents, undefined, false)).toBeUndefined();
    });

    it("preserves an explicit selection in either mode", () =>
    {
        expect(resolvePreviewDocumentSelection(documents, "second", false)?.id).toBe("second");
    });
});
