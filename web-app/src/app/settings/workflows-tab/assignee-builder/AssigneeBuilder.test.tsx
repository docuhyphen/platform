/** @vitest-environment jsdom */
import {cleanup, render, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import AssigneeBuilder from "./AssigneeBuilder.tsx";

const fetchOrganizationGroups = vi.fn();
const fetchOrganizationUsers = vi.fn();

vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUserPersonOrganization: {id: "organization-1", isActive: true},
    }),
}));

vi.mock("../../../../services/organizationApi.ts", () => ({
    fetchOrganizationGroups: (...args: unknown[]) => fetchOrganizationGroups(...args),
    fetchOrganizationUsers: (...args: unknown[]) => fetchOrganizationUsers(...args),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("AssigneeBuilder", () =>
{
    it("does not load tenant directory data in platform mode", async () =>
    {
        render(
            <AssigneeBuilder
                assignees={[]}
                onChange={vi.fn()}
                allowTenantDirectory={false}/>,
        );

        await waitFor(() =>
        {
            expect(fetchOrganizationUsers).not.toHaveBeenCalled();
            expect(fetchOrganizationGroups).not.toHaveBeenCalled();
        });
    });
});
