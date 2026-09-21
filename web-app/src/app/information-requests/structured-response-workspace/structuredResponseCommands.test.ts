import {beforeEach, describe, expect, it, vi} from "vitest";
import {InformationRequestResponseDisposition} from "../../models/models.tsx";
import {structuredResponseCommands} from "./structuredResponseCommands.ts";

const patchInformationRequestResponses = vi.fn();
const addInformationRequestGroupOccurrence = vi.fn();
const removeInformationRequestGroupOccurrence = vi.fn();
const reorderInformationRequestGroupOccurrences = vi.fn();

vi.mock("../../../services/informationRequestRuntimeService.ts", () => ({
    patchInformationRequestResponses: (...args: unknown[]) => patchInformationRequestResponses(...args),
    addInformationRequestGroupOccurrence: (...args: unknown[]) => addInformationRequestGroupOccurrence(...args),
    removeInformationRequestGroupOccurrence: (...args: unknown[]) => removeInformationRequestGroupOccurrence(...args),
    reorderInformationRequestGroupOccurrences: (...args: unknown[]) => reorderInformationRequestGroupOccurrences(...args),
}));

const patchRequest = {
    patches: [
        {
            requirementId: "requirement-1",
            disposition: InformationRequestResponseDisposition.PROVIDED,
        },
    ],
    confirmedHiddenResponseClearRequirementIds: [],
};

describe("structuredResponseCommands", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("saves responses through the authenticated surface with a fresh idempotency key", async () =>
    {
        patchInformationRequestResponses.mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            data: [{informationRequestRequirementId: "requirement-1"}],
        });
        const keys = ["key-1", "key-2"];
        const commands = structuredResponseCommands({newIdempotencyKey: () => keys.shift() ?? ""});

        const result = await commands.saveResponses("request-1", patchRequest, "\"responses:1\"");

        expect(patchInformationRequestResponses).toHaveBeenCalledWith(
            "request-1",
            patchRequest,
            {expectedETag: "\"responses:1\"", idempotencyKey: "key-1", accessLinkToken: undefined},
        );
        expect(result).toEqual({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            responses: [{informationRequestRequirementId: "requirement-1"}],
        });

        await commands.saveResponses("request-1", patchRequest, "\"responses:2\"");

        expect(patchInformationRequestResponses).toHaveBeenLastCalledWith(
            "request-1",
            patchRequest,
            {expectedETag: "\"responses:2\"", idempotencyKey: "key-2", accessLinkToken: undefined},
        );
    });

    it("carries the access link token on every shared no-auth command", async () =>
    {
        const savedOccurrences = {
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: [{id: "occurrence-1"}],
        };
        patchInformationRequestResponses.mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: [{informationRequestRequirementId: "requirement-1"}],
        });
        addInformationRequestGroupOccurrence.mockResolvedValue(savedOccurrences);
        removeInformationRequestGroupOccurrence.mockResolvedValue(savedOccurrences);
        reorderInformationRequestGroupOccurrences.mockResolvedValue(savedOccurrences);
        const commands = structuredResponseCommands({
            accessLinkToken: "access-token",
            newIdempotencyKey: () => "key-1",
        });

        await commands.saveResponses("request-1", patchRequest, "\"responses:2\"");
        const added = await commands.addOccurrence("request-1", {groupKey: "reported-item"}, "\"responses:2\"");
        await commands.removeOccurrence("request-1", "occurrence-1", "\"responses:2\"");
        await commands.reorderOccurrences(
            "request-1",
            {groupKey: "reported-item", occurrenceIds: ["occurrence-1"]},
            "\"responses:2\"",
        );

        expect(added).toEqual({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            occurrences: [{id: "occurrence-1"}],
        });
        const expectedOptions = {
            expectedETag: "\"responses:2\"",
            idempotencyKey: "key-1",
            accessLinkToken: "access-token",
        };
        expect(patchInformationRequestResponses)
            .toHaveBeenCalledWith("request-1", patchRequest, expectedOptions);
        expect(addInformationRequestGroupOccurrence)
            .toHaveBeenCalledWith("request-1", {groupKey: "reported-item"}, expectedOptions);
        expect(removeInformationRequestGroupOccurrence)
            .toHaveBeenCalledWith("request-1", "occurrence-1", expectedOptions);
        expect(reorderInformationRequestGroupOccurrences).toHaveBeenCalledWith(
            "request-1",
            {groupKey: "reported-item", occurrenceIds: ["occurrence-1"]},
            expectedOptions,
        );
    });

    it("reports a stale refusal without inventing an ETag", async () =>
    {
        patchInformationRequestResponses.mockResolvedValue({outcome: "STALE"});
        const commands = structuredResponseCommands({newIdempotencyKey: () => "key-1"});

        expect(await commands.saveResponses("request-1", patchRequest, "\"responses:1\""))
            .toEqual({outcome: "STALE"});
    });
});

