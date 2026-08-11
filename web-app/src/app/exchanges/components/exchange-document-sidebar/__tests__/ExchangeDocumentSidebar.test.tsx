/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, cleanup} from "@testing-library/react";
import ExchangeDocumentSidebar from "../ExchangeDocumentSidebar.tsx";
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus, PlanFeature} from "../../../../models/models.tsx";

const mockHasCapability = vi.fn();
const mockPlanFeatures = vi.hoisted(() => ({
    available: new Set<PlanFeature>(),
}));

vi.mock("../../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUser: {id: "user-1"},
        hasCapability: mockHasCapability,
    }),
}));

vi.mock("../../../../../hooks/subscription/usePlanFeature.ts", () => ({
    usePlanFeature: (feature: PlanFeature) => ({
        isKnown: true,
        isIncluded: mockPlanFeatures.available.has(feature),
        isEnforced: true,
        isDiscoverable: mockPlanFeatures.available.has(feature),
        isAvailable: mockPlanFeatures.available.has(feature),
        upgradePlanCode: null,
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
    mockPlanFeatures.available.clear();
});

describe("ExchangeDocumentSidebar plan tab visibility", () =>
{
    it("always shows Notes and Comments for a Free plan user", () =>
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

        expect(document.getElementById("comments")).toBeTruthy();
        expect(document.getElementById("versions")).toBeFalsy();
    });

    it("shows Versions when document version history is included", () =>
    {
        mockHasCapability.mockReturnValue(false);
        mockPlanFeatures.available.add(PlanFeature.DOCUMENT_VERSION_HISTORY);

        render(
            <ExchangeDocumentSidebar
                onOpen={vi.fn()}
                isOpen={true}
                exchangeDocument={documentFixture}
                exchange={exchangeFixture}
            />
        );

        expect(document.getElementById("comments")).toBeTruthy();
        expect(document.getElementById("versions")).toBeTruthy();
    });
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
