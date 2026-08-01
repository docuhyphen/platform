/** @vitest-environment jsdom */
import {cleanup, render, screen, waitFor} from "@testing-library/react";
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
    it("shows the selected user when editing a principal assignee", async () =>
    {
        fetchOrganizationUsers.mockResolvedValue([
            {
                id: "user-1",
                email: "jordan@example.com",
                person: {
                    firstName: "Jordan",
                    lastName: "Lee",
                },
                avatarUrl: null,
            },
        ]);
        fetchOrganizationGroups.mockResolvedValue([]);

        render(
            <AssigneeBuilder
                assignees={[
                    {
                        kind: "PRINCIPAL",
                        principalKind: "USER",
                        principalId: "user-1",
                    },
                ]}
                onChange={vi.fn()}/>,
        );

        await waitFor(() =>
        {
            const input = screen.getByPlaceholderText("Find a user") as HTMLInputElement;
            expect(input.value).toBe("Jordan Lee (jordan@example.com)");
        });
    });

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
