import {describe, expect, it} from "vitest";
import {DocumentType, ExchangeRequestDocumentRequest} from "../models/models.tsx";
import {exchangeInitiationDocumentProblem} from "./exchangeInitiationDocuments.ts";

const requested = (overrides: Partial<ExchangeRequestDocumentRequest> = {}): ExchangeRequestDocumentRequest => ({
    title: "Requested record",
    restrictType: false,
    ...overrides,
});

describe("exchangeInitiationDocumentProblem", () => {
    it("asks for a document when the Exchange's Blueprint, if any, starts no Information Request", () => {
        expect(exchangeInitiationDocumentProblem([], false)).toBe("Add at least one document");
    });

    it("lets an Exchange whose Blueprint's Information Request collects the documents start without any", () => {
        expect(exchangeInitiationDocumentProblem([], true)).toBeNull();
    });

    it("still checks every document that is added", () => {
        expect(exchangeInitiationDocumentProblem([requested({title: ""})], true)).toBe("All documents must have names");
        expect(exchangeInitiationDocumentProblem([requested({restrictType: true})], false))
            .toBe("All restricted documents must have a type");
        expect(exchangeInitiationDocumentProblem([requested({
            restrictType: true,
            restrictedType: DocumentType.PDF
        })], false))
            .toBeNull();
    });
});
