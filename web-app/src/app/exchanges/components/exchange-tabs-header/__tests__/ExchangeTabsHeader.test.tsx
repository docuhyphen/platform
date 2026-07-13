/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, cleanup} from "@testing-library/react";
import ExchangeTabsHeader from "../ExchangeTabsHeader.tsx";

afterEach(() =>
{
    cleanup();
});

describe("ExchangeTabsHeader audit tab visibility", () =>
{
    it("hides the Audit tab for a user without ORG_AUDIT_READ", () =>
    {
        render(
            <ExchangeTabsHeader
                activeTab={"documents"}
                documents={[]}
                canDownloadZip={false}
                canViewAudit={false}
                onTabChange={vi.fn()}
                onDownloadZip={vi.fn()}
            />
        );

        expect(document.getElementById("exchange-audit-tab")).toBeFalsy();
    });

    it("shows the Audit tab for a user with ORG_AUDIT_READ", () =>
    {
        render(
            <ExchangeTabsHeader
                activeTab={"documents"}
                documents={[]}
                canDownloadZip={false}
                canViewAudit={true}
                onTabChange={vi.fn()}
                onDownloadZip={vi.fn()}
            />
        );

        expect(document.getElementById("exchange-audit-tab")).toBeTruthy();
    });
});
