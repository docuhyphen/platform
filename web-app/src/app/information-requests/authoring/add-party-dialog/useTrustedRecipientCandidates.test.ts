// @vitest-environment jsdom
import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    getInformationRequestTrustedRecipientCandidates
} from "../../../../services/informationRequestAuthoringService.ts";
import {InformationRequestTrustedRecipientCandidateDto} from "../../../models/models.tsx";
import {useTrustedRecipientCandidates} from "./useTrustedRecipientCandidates.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestTrustedRecipientCandidates: vi.fn(),
}));

const candidate = (
    exchangeRecipientId: string,
    principalKind: InformationRequestTrustedRecipientCandidateDto["principalKind"],
): InformationRequestTrustedRecipientCandidateDto => ({
    exchangeRecipientId,
    selectionType: principalKind === "PRINCIPAL_GROUP" ? "TRUSTED_GROUP" : "TRUSTED_PERSON",
    principalKind,
    principalId: `principal-${exchangeRecipientId}`,
    label: `Holder ${exchangeRecipientId}`,
    targetOrganizationName: "Partner organization",
    trustSuspended: false,
});

describe("useTrustedRecipientCandidates", () => {
    beforeEach(() => vi.clearAllMocks());

    it("offers the trusted accounts and groups of the Exchange by name and leaves participants out", async () => {
        vi.mocked(getInformationRequestTrustedRecipientCandidates).mockResolvedValue([
            candidate("recipient-a", "USER"),
            candidate("recipient-b", "PRINCIPAL_GROUP"),
            candidate("recipient-c", "PARTICIPANT"),
        ]);

        const {result} = renderHook(() => useTrustedRecipientCandidates("request-a"));

        await waitFor(() => expect(result.current.candidates).toHaveLength(2));
        expect(result.current.candidates.map(entry => entry.name)).toEqual([
            "Holder recipient-a (Partner organization)",
            "Holder recipient-b (Partner organization)",
        ]);
        expect(result.current.error).toBeNull();
    });

    it("states a failed read instead of offering no trusted recipient, and reads again on reload", async () => {
        vi.mocked(getInformationRequestTrustedRecipientCandidates)
            .mockRejectedValueOnce({status: 500})
            .mockResolvedValueOnce([candidate("recipient-a", "USER")]);

        const {result} = renderHook(() => useTrustedRecipientCandidates("request-a"));

        await waitFor(() => expect(result.current.error).toBe(
            "The trusted recipients who can be named could not be loaded.",
        ));
        expect(result.current.candidates).toEqual([]);

        await act(async () => result.current.reload());

        expect(result.current.error).toBeNull();
        expect(result.current.candidates.map(entry => entry.exchangeRecipientId)).toEqual(["recipient-a"]);
    });

    it("offers no trusted recipient and states nothing to a caller who may not read them", async () => {
        vi.mocked(getInformationRequestTrustedRecipientCandidates).mockRejectedValue({
            errorMessage: "Access denied",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
        });

        const {result} = renderHook(() => useTrustedRecipientCandidates("request-a"));

        await waitFor(() => expect(getInformationRequestTrustedRecipientCandidates).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(result.current.candidates).toEqual([]));
        expect(result.current.error).toBeNull();
    });
});
