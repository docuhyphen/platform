import {ExchangeRequestDocumentRequest} from "../models/models.tsx";

export const exchangeInitiationDocumentProblem = (
    documents: ExchangeRequestDocumentRequest[],
    documentsOptional: boolean,
): string | null => {
    if (documents.length === 0) return documentsOptional ? null : "Add at least one document";
    if (documents.some(document => !document.title)) return "All documents must have names";
    if (documents.some(document => document.restrictType && !document.restrictedType))
        return "All restricted documents must have a type";
    return null;
};
