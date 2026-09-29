import {describe, expect, it} from "vitest";
import {Capability, InformationRequestOwnerType} from "../../../models/models.tsx";
import {mayCorrectAsOwner} from "./ownerPrivacyAccess.ts";

const holds = (capabilities: Capability[]) => (capability: Capability) => capabilities.includes(capability);

describe("mayCorrectAsOwner", () =>
{
    it("lets a personal owner correct their own request and an organization's privacy manager correct its requests", () =>
    {
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.USER, ownerUserId: "user-a"},
            {userId: "user-a", activeOrganizationId: null},
            holds([]),
        )).toBe(true);
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"},
            {userId: "user-a", activeOrganizationId: "organization-a"},
            holds([Capability.INFORMATION_REQUEST_PRIVACY_MANAGE]),
        )).toBe(true);
    });

    it("refuses another owner's request, the wrong session, and a member without privacy management", () =>
    {
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.USER, ownerUserId: "user-b"},
            {userId: "user-a", activeOrganizationId: null},
            holds([]),
        )).toBe(false);
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"},
            {userId: "user-a", activeOrganizationId: null},
            holds([Capability.INFORMATION_REQUEST_PRIVACY_MANAGE]),
        )).toBe(false);
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"},
            {userId: "user-a", activeOrganizationId: "organization-b"},
            holds([Capability.INFORMATION_REQUEST_PRIVACY_MANAGE]),
        )).toBe(false);
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"},
            {userId: "user-a", activeOrganizationId: "organization-a"},
            holds([]),
        )).toBe(false);
        expect(mayCorrectAsOwner(
            {ownerType: InformationRequestOwnerType.USER, ownerUserId: "user-a"},
            null,
            holds([]),
        )).toBe(false);
    });
});
