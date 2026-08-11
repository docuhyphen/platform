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
) => render(
    <ExchangeTabsHeader
        activeTab={"documents"}
        documents={[]}
        canDownloadZip={false}
        canViewAudit={canViewAudit}
        canViewDetails={canViewDetails}
        canViewWorkflow={canViewWorkflow}
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
