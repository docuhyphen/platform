/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, cleanup} from "@testing-library/react";
import ExchangeDocumentSidebar from "../ExchangeDocumentSidebar.tsx";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../../models/models.tsx";

const mockHasCapability = vi.fn();

vi.mock("../../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUser: {id: "user-1"},
        hasCapability: mockHasCapability,
    }),
}));

vi.mock("../exchange-document-audit/ExchangeDocumentAudit.tsx", () => ({
    default: () => <div id={"stub-exchange-document-audit"}/>,
}));

vi.mock("../exchange-document-comments/ExchangeDocumentComments.tsx", () => ({
    default: () => <div id={"stub-exchange-document-comments"}/>,
}));

vi.mock("../exchange-document-versions/ExchangeDocumentVersions.tsx", () => ({
    default: () => <div id={"stub-exchange-document-versions"}/>,
}));

const exchangeFixture = {
    id: "exchange-1",
    status: ExchangeStatus.ACCEPTED_STARTED,
    initiator: {id: "user-1"},
    documents: [],
} as unknown as ExchangeDetailedDto;

const documentFixture: DocumentDetailedDto = {
    id: "doc-1",
    title: "Document 1",
} as unknown as DocumentDetailedDto;

afterEach(() =>
{
    cleanup();
    mockHasCapability.mockReset();
});

describe("ExchangeDocumentSidebar audit tab visibility", () =>
{
    it("hides the Audit tab for a user without ORG_AUDIT_READ", () =>
    {
        mockHasCapability.mockReturnValue(false);

        render(
            <ExchangeDocumentSidebar
                onOpen={vi.fn()}
                isOpen={true}
                exchangeDocument={documentFixture}
                exchange={exchangeFixture}
            />
        );

        expect(document.getElementById("audit")).toBeFalsy();
    });

    it("shows the Audit tab for a user with ORG_AUDIT_READ", () =>
    {
        mockHasCapability.mockReturnValue(true);

        render(
            <ExchangeDocumentSidebar
                onOpen={vi.fn()}
                isOpen={true}
                exchangeDocument={documentFixture}
                exchange={exchangeFixture}
            />
        );

        expect(document.getElementById("audit")).toBeTruthy();
    });
});
