/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestState,
} from "../../../models/models.tsx";
import {useFollowUps} from "./useFollowUps.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestLineage: vi.fn()}));

const request: InformationRequestDto = {
    id: "request-a",
    exchangeId: "exchange-a",
    templateVersionId: "version-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    ownerOrganizationId: "organization-a",
    state: InformationRequestState.ISSUED,
    gatesExchangeClosure: true,
    aggregateRevision: 1,
    createdAt: 0,
    updatedAt: 0,
    requestETag: "\"request-1\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
};

describe("useFollowUps", () => {
    beforeEach(() => {
        vi.mocked(authoring.getInformationRequestLineage).mockReset();
    });

    afterEach(cleanup);

    it("does not turn a refused lineage read into a successful empty result", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockRejectedValue({errorMessage: "Lineage access refused"});
        const {result} = renderHook(() => useFollowUps(request, vi.fn()));

        await waitFor(() => expect(result.current.loadError).toBe("Lineage access refused"));
        expect(result.current.lineage).toBeNull();
        expect(result.current.error).toBeNull();
    });

    it("still represents a successful empty lineage", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        const {result} = renderHook(() => useFollowUps(request, vi.fn()));

        await waitFor(() => expect(result.current.lineage?.successors).toEqual([]));
        expect(result.current.loadError).toBeNull();
    });

    it("reads the lineage again on reload and drops the refusal once it answers", async () => {
        vi.mocked(authoring.getInformationRequestLineage)
            .mockRejectedValueOnce({errorMessage: "Lineage access refused"})
            .mockResolvedValueOnce({informationRequestId: "request-a", successors: []});
        const {result} = renderHook(() => useFollowUps(request, vi.fn()));
        await waitFor(() => expect(result.current.loadError).not.toBeNull());

        await act(async () => result.current.reload());

        expect(result.current.loadError).toBeNull();
        expect(result.current.lineage?.successors).toEqual([]);
    });
});
