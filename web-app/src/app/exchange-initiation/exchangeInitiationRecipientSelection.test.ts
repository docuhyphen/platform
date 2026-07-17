import {describe, expect, it} from "vitest";
import {ExchangeInitiationRecipientMode} from "./components/exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx";
import {buildRecipientSelection} from "./exchangeInitiationRecipientSelection.ts";

describe("buildRecipientSelection", () =>
{
    it("builds a trusted group selection without client-supplied attestation fields", () =>
    {
        const selection = buildRecipientSelection({
            mode: ExchangeInitiationRecipientMode.TRUSTED_ORG,
            organization: {
                id: "organization-2",
                name: "Partner",
                registrationNumber: "",
                verificationComplete: true,
                isActive: true,
            },
            group: {
                id: "group-1",
                name: "Published Group",
                organizationId: "organization-2",
                members: [],
            },
        });

        expect(selection).toEqual({
            type: "TRUSTED_GROUP",
            organizationId: "organization-2",
            groupId: "group-1",
        });
    });

    it("builds a trusted person selection from a resolution id without client-supplied identity", () =>
    {
        const selection = buildRecipientSelection({
            mode: ExchangeInitiationRecipientMode.TRUSTED_ORG,
            organization: {
                id: "organization-2",
                name: "Partner",
                registrationNumber: "",
                verificationComplete: true,
                isActive: true,
            },
            resolutionId: "resolution-1",
        });

        expect(selection).toEqual({type: "TRUSTED_PERSON", resolutionId: "resolution-1"});
    });

    it("prefers a verified person over a published group in a Trusted Organization", () =>
    {
        const selection = buildRecipientSelection({
            mode: ExchangeInitiationRecipientMode.TRUSTED_ORG,
            organization: {
                id: "organization-2",
                name: "Partner",
                registrationNumber: "",
                verificationComplete: true,
                isActive: true,
            },
            group: {
                id: "group-1",
                name: "Published Group",
                organizationId: "organization-2",
                members: [],
            },
            resolutionId: "resolution-1",
        });

        expect(selection.type).toBe("TRUSTED_PERSON");
    });

    it("keeps a general email invitation separate from trusted resolution", () =>
    {
        const selection = buildRecipientSelection({
            mode: ExchangeInitiationRecipientMode.PEOPLE,
            externalRecipient: {
                email: "person@example.test",
                firstName: "Test",
                lastName: "Person",
            },
        });

        expect(selection.type).toBe("EXTERNAL_EMAIL");
    });
});
