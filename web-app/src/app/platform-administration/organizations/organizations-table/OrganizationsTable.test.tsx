/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {PlatformOrganizationSummary} from "../../../../services/types/platformOrganizations.ts";
import OrganizationsTable from "./OrganizationsTable.tsx";

afterEach(cleanup);

describe("OrganizationsTable", () =>
{
    it("renders only restricted platform account summary fields", () =>
    {
        const organization: PlatformOrganizationSummary & {members: string[]; authenticationSecret: string} = {
            organizationId: "organization-1",
            name: "Acme",
            registrationNumber: "REG-1",
            active: true,
            verificationComplete: true,
            createdDate: "2026-07-28T00:00:00Z",
            tierCode: "PRO",
            maxUsers: 50,
            activeUsers: 12,
            featureEntitlements: [{featureCode: "WORKFLOWS", enabled: true}],
            members: ["Tenant Member"],
            authenticationSecret: "tenant-secret",
        };

        render(
            <OrganizationsTable
                organizations={[organization]}
                onEdit={vi.fn()}/>,
        );

        expect(screen.getByText("Acme")).toBeTruthy();
        expect(screen.getByText("Registration number")).toBeTruthy();
        expect(screen.getByText("REG-1")).toBeTruthy();
        expect(screen.getByText("12 / 50")).toBeTruthy();
        expect(screen.getByText("WORKFLOWS: On")).toBeTruthy();
        expect(screen.queryByText("Tenant Member")).toBeNull();
        expect(screen.queryByText("tenant-secret")).toBeNull();
        const headers = Array.from(
            document.querySelectorAll("#platform-organizations-header-row th"),
        ).map((header) => header.textContent);
        expect(headers.slice(0, 3)).toEqual([
            "Organization",
            "Registration number",
            "Status",
        ]);
    });

    it("keeps the column header inside the scrollable table region", () =>
    {
        render(
            <OrganizationsTable
                organizations={[]}
                onEdit={vi.fn()}/>,
        );

        const scrollContainer = document.getElementById(
            "platform-organizations-table-scroll-container",
        ) as HTMLElement;
        const tableHeader = document.getElementById(
            "platform-organizations-table-header",
        ) as HTMLElement;

        expect(scrollContainer.contains(tableHeader)).toBe(true);
    });
});
