import {describe, expect, it} from "vitest";
import {InformationRequestOwnerType} from "../../models/models.tsx";
import {ownerWorkspaceOf, workspaceScopeOf} from "./ownerWorkspace.ts";

const organizationOwner = {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"};
const personalOwner = {ownerType: InformationRequestOwnerType.USER, ownerUserId: "user-a"};

describe("ownerWorkspaceOf", () => {
    it("is the current workspace only when the session works for the owner itself", () => {
        expect(ownerWorkspaceOf(organizationOwner, {userId: "user-a", activeOrganizationId: "organization-a"}))
            .toEqual({kind: "current", ownerKey: "organization:organization-a"});
        expect(ownerWorkspaceOf(personalOwner, {userId: "user-a", activeOrganizationId: null}))
            .toEqual({kind: "current", ownerKey: "person:user-a"});
    });

    it("tells another organization, a personal session, and another person apart from the owner", () => {
        expect(ownerWorkspaceOf(organizationOwner, {
            userId: "user-a",
            activeOrganizationId: "organization-b"
        }).kind).toBe("other");
        expect(ownerWorkspaceOf(organizationOwner, {userId: "user-a", activeOrganizationId: null}).kind).toBe("other");
        expect(ownerWorkspaceOf(personalOwner, {
            userId: "user-a",
            activeOrganizationId: "organization-a"
        }).kind).toBe("other");
        expect(ownerWorkspaceOf(personalOwner, {userId: "user-b", activeOrganizationId: null}).kind).toBe("other");
    });

    it("waits while the session or the owner is not known yet", () => {
        expect(ownerWorkspaceOf(organizationOwner, null).kind).toBe("pending");
        expect(ownerWorkspaceOf(null, {userId: "user-a", activeOrganizationId: "organization-a"}).kind).toBe("pending");
        expect(ownerWorkspaceOf({ownerType: InformationRequestOwnerType.ORGANIZATION}, {
            userId: "user-a",
            activeOrganizationId: null
        }).kind)
            .toBe("pending");
    });

    it("scopes reads by the owner's identity rather than its kind", () => {
        const first = workspaceScopeOf(ownerWorkspaceOf(organizationOwner, {
            userId: "user-a",
            activeOrganizationId: "organization-a"
        }));
        const second = workspaceScopeOf(ownerWorkspaceOf(
            {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-b"},
            {userId: "user-a", activeOrganizationId: "organization-b"},
        ));

        expect(first).not.toBe(second);
    });
});
