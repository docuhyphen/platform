/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, cleanup} from "@testing-library/react";
import ExchangeTabsHeader from "../ExchangeTabsHeader.tsx";

afterEach(() =>
{
    cleanup();
});

const renderHeader = (
    canViewDetails: boolean,
    canViewWorkflow: boolean,
    canViewAudit = false,
    canViewInformationRequests = false,
) => render(
    <ExchangeTabsHeader
        activeTab={"documents"}
        documents={[]}
        canDownloadZip={false}
        canViewAudit={canViewAudit}
        canViewDetails={canViewDetails}
        canViewWorkflow={canViewWorkflow}
        canViewInformationRequests={canViewInformationRequests}
        isDocumentToolbarVisible={false}
        onTabChange={vi.fn()}
        onDownloadZip={vi.fn()}
        onToggleDocumentToolbar={vi.fn()}
    />
);

describe("ExchangeTabsHeader plan tab visibility", () =>
{
    it("shows only Documents for a Free plan user without audit access", () =>
    {
        renderHeader(false, false);

        expect(document.getElementById("exchange-documents-tab")).toBeTruthy();
        expect(document.getElementById("exchange-details-tab")).toBeFalsy();
        expect(document.getElementById("exchange-workflow-tab")).toBeFalsy();
        expect(document.getElementById("exchange-audit-tab")).toBeFalsy();
    });

    it("hides Business-only tabs for a Personal plan user", () =>
    {
        renderHeader(false, false);

        expect(document.getElementById("exchange-details-tab")).toBeFalsy();
        expect(document.getElementById("exchange-workflow-tab")).toBeFalsy();
    });

    it("shows Business Fields and Workflow tabs for a Business plan user", () =>
    {
        renderHeader(true, true);

        expect(document.getElementById("exchange-details-tab")).toBeTruthy();
        expect(document.getElementById("exchange-workflow-tab")).toBeTruthy();
    });
});

describe("ExchangeTabsHeader Information Requests tab", () =>
{
    it("shows one Information Requests tab apart from the Fields tab when the server lists work or offers creation", () =>
    {
        renderHeader(true, false, false, true);

        expect(document.getElementById("exchange-information-requests-tab-trigger")?.textContent).toContain("Information Requests");
        expect(document.getElementById("exchange-details-tab")).toBeTruthy();
    });

    it("is absent when nothing is listed or offered, even for a user whose own plan shows Fields", () =>
    {
        renderHeader(true, true, true, false);

        expect(document.getElementById("exchange-information-requests-tab-trigger")).toBeFalsy();
    });
});

describe("ExchangeTabsHeader audit tab visibility", () =>
{
    it("hides the Audit tab for a user without ORG_AUDIT_READ", () =>
    {
        renderHeader(true, true, false);

        expect(document.getElementById("exchange-audit-tab")).toBeFalsy();
    });

    it("shows the Audit tab for a user with ORG_AUDIT_READ", () =>
    {
        renderHeader(true, true, true);

        expect(document.getElementById("exchange-audit-tab")).toBeTruthy();
    });
});
