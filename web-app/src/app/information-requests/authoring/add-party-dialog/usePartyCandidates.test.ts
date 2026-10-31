// @vitest-environment jsdom
import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {fetchOrganizationGroups, fetchOrganizationUsers} from "../../../../services/organizationApi.ts";
import {fetchPersonalGroups} from "../../../../services/meGroupsApi.ts";
import {fetchRecentContacts} from "../../../../services/personalContactsApi.ts";
import {getInformationRequestSubjects} from "../../../../services/informationRequestAuthoringService.ts";
import {InformationRequestOwnerType} from "../../../models/models.tsx";
import {usePartyCandidates} from "./usePartyCandidates.ts";

const session = vi.hoisted(() => ({activeOrganizationId: undefined as string | undefined}));

vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: {userId: "user-a", activeOrganizationId: session.activeOrganizationId}}),
}));

const organizationOwner = (organizationId: string) => ({
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    ownerOrganizationId: organizationId,
});

const personalOwner = {ownerType: InformationRequestOwnerType.USER, ownerUserId: "user-a"};
vi.mock("../../../../services/organizationApi.ts", () => ({
    fetchOrganizationGroups: vi.fn(),
    fetchOrganizationUsers: vi.fn(),
}));
vi.mock("../../../../services/meGroupsApi.ts", () => ({fetchPersonalGroups: vi.fn()}));
vi.mock("../../../../services/personalContactsApi.ts", () => ({fetchRecentContacts: vi.fn()}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestSubjects: vi.fn().mockResolvedValue([]),
}));

describe("usePartyCandidates", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(fetchOrganizationGroups).mockResolvedValue([]);
        vi.mocked(fetchPersonalGroups).mockResolvedValue([]);
        vi.mocked(getInformationRequestSubjects).mockResolvedValue([]);
    });

    it("offers the active organization's members with an active account", async () => {
        session.activeOrganizationId = "org-a";
        vi.mocked(fetchOrganizationUsers).mockResolvedValue([
            {
                id: "user-b",
                email: "second@process.test",
                person: {firstName: "Second", lastName: "Member"},
                isActive: true,
                appRoles: [],
                organizationRoles: []
            },
            {id: "user-c", email: "third@process.test", isActive: true, appRoles: [], organizationRoles: []},
            {id: "user-d", email: "former@process.test", isActive: false, appRoles: [], organizationRoles: []},
        ]);

        const {result} = renderHook(() => usePartyCandidates(organizationOwner("org-a")));

        await waitFor(() => expect(result.current.people).toEqual([
            {id: "user-b", name: "Second Member (second@process.test)"},
            {id: "user-c", name: "third@process.test"},
        ]));
        expect(fetchOrganizationUsers).toHaveBeenCalledWith("org-a");
        expect(fetchRecentContacts).not.toHaveBeenCalled();
    });

    it("offers personal contacts who have an account when no organization is selected", async () => {
        session.activeOrganizationId = undefined;
        vi.mocked(fetchRecentContacts).mockResolvedValue([
            {
                contactAppUserId: "user-e",
                email: "contact@process.test",
                firstName: "Known",
                lastName: "Contact",
                lastSharedAt: "2026-09-01T08:00:00Z",
                shareCount: 2
            },
            {
                contactAppUserId: null,
                email: "no-account@process.test",
                firstName: null,
                lastName: null,
                lastSharedAt: "2026-09-02T08:00:00Z",
                shareCount: 1
            },
        ]);

        const {result} = renderHook(() => usePartyCandidates(personalOwner));

        await waitFor(() => expect(result.current.people).toEqual([
            {id: "user-e", name: "Known Contact (contact@process.test)"},
        ]));
        expect(fetchRecentContacts).toHaveBeenCalledWith(50);
        expect(fetchOrganizationUsers).not.toHaveBeenCalled();
    });

    it.each([
        ["a personal request while an organization is active", "org-a", personalOwner],
        ["another organization's request", "org-a", organizationOwner("org-b")],
        ["an organization's request while no organization is active", undefined, organizationOwner("org-a")],
    ])("reads no people, groups or subjects for %s and says it belongs to another workspace", async (_, active, owner) => {
        session.activeOrganizationId = active;

        const {result} = renderHook(() => usePartyCandidates(owner));

        await waitFor(() => expect(result.current.otherWorkspace).toBe(true));
        expect(result.current.people).toEqual([]);
        expect(result.current.groups).toEqual([]);
        expect(result.current.subjects).toEqual([]);
        expect(fetchOrganizationUsers).not.toHaveBeenCalled();
        expect(fetchOrganizationGroups).not.toHaveBeenCalled();
        expect(fetchRecentContacts).not.toHaveBeenCalled();
        expect(fetchPersonalGroups).not.toHaveBeenCalled();
        expect(getInformationRequestSubjects).not.toHaveBeenCalled();
    });

    it("states a refused read of the people who can be named and lists them once a reload loads", async () => {
        session.activeOrganizationId = "org-a";
        vi.mocked(fetchOrganizationUsers)
            .mockRejectedValueOnce({errorMessage: "The members could not be read."})
            .mockResolvedValueOnce([{
                id: "user-b",
                email: "second@process.test",
                isActive: true,
                appRoles: [],
                organizationRoles: []
            }]);

        const {result} = renderHook(() => usePartyCandidates(organizationOwner("org-a")));

        await waitFor(() => expect(result.current.error).toBe("The members could not be read."));
        expect(result.current.people).toEqual([]);
        await act(async () => result.current.reload());
        expect(result.current.people).toEqual([{id: "user-b", name: "second@process.test"}]);
        expect(result.current.error).toBeNull();
    });

    it("keeps the subject registry closed without an error for a caller who may not read it, and states any other refused read", async () => {
        session.activeOrganizationId = "org-a";
        vi.mocked(fetchOrganizationUsers).mockResolvedValue([]);
        vi.mocked(getInformationRequestSubjects)
            .mockRejectedValueOnce({errorMessage: "Forbidden", reasonCode: "INFORMATION_REQUEST_FORBIDDEN"})
            .mockRejectedValueOnce({errorMessage: "The subjects could not be read."});

        const {result} = renderHook(() => usePartyCandidates(organizationOwner("org-a")));

        await waitFor(() => expect(getInformationRequestSubjects).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(result.current.loading).toBe(false));
        expect(result.current.subjectRegistryAvailable).toBe(false);
        expect(result.current.error).toBeNull();
        await act(async () => result.current.reload());
        expect(result.current.subjectRegistryAvailable).toBe(false);
        expect(result.current.error).toBe("The subjects could not be read.");
    });
});

