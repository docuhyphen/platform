// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    changeRecordPreservationHoldScope,
    placeRecordPreservationHold,
    publishRecordRetentionSchedule,
    releaseRecordPreservationHold,
} from "../recordPreservationService.ts";
import {PlaceRecordPreservationHoldRequest, RecordPreservationScope} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn()}}));

describe("record preservation transport", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(apiClient.post).mockResolvedValue({data: {}});
        vi.mocked(apiClient.patch).mockResolvedValue({data: {}});
        vi.mocked(apiClient.put).mockResolvedValue({data: {}});
    });

    it("places, rescopes, and releases a hold and publishes a schedule under their Idempotency-Keys and read versions", async () => {
        const request = {reason: "review.pending"} as unknown as PlaceRecordPreservationHoldRequest;
        const scope = {} as RecordPreservationScope;

        await placeRecordPreservationHold(request, "place-key");
        expect(apiClient.post).toHaveBeenLastCalledWith("/record-preservation-holds", request, {headers: {"Idempotency-Key": "place-key"}});
        await changeRecordPreservationHoldScope("hold-a", scope, "scope.widened", "\"hold-a:1\"", "scope-key");
        expect(apiClient.patch).toHaveBeenLastCalledWith(
            "/record-preservation-holds/hold-a/scope",
            {scope, reason: "scope.widened"},
            {headers: {"Idempotency-Key": "scope-key", "If-Match": "\"hold-a:1\""}},
        );
        await releaseRecordPreservationHold("hold-a", "review.closed", "\"hold-a:2\"", "release-key");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/record-preservation-holds/hold-a/release",
            {reason: "review.closed"},
            {headers: {"Idempotency-Key": "release-key", "If-Match": "\"hold-a:2\""}},
        );
        await publishRecordRetentionSchedule("INFORMATION_REQUEST", {
            minimumRetentionDays: 30,
            disposalAfterDays: 60
        }, "\"schedule:0\"", "publish-key");
        expect(apiClient.put).toHaveBeenLastCalledWith(
            "/record-retention-schedules/INFORMATION_REQUEST",
            {minimumRetentionDays: 30, disposalAfterDays: 60},
            {headers: {"Idempotency-Key": "publish-key", "If-Match": "\"schedule:0\""}},
        );
    });
});
