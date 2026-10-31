import {beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestExchangeListingDto,
    InformationRequestState,
    InformationRequestSummaryDto,
} from "../models/models.tsx";
import {findStartedInformationRequest} from "./startedInformationRequest.ts";

vi.mock("../../services/informationRequestAuthoringService.ts", () => ({
    getExchangeInformationRequests: vi.fn(),
}));

const listing = (requests: Partial<InformationRequestSummaryDto>[]): InformationRequestExchangeListingDto => ({
    requests: requests as InformationRequestSummaryDto[],
    canCreate: true,
});

describe("findStartedInformationRequest", () => {
    beforeEach(() => vi.clearAllMocks());

    it("names the request an Exchange started from its Blueprint", async () => {
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue(listing([
            {id: "request-a", title: "Periodic records", state: InformationRequestState.DRAFT},
        ]));

        await expect(findStartedInformationRequest("exchange-a")).resolves.toEqual({
            id: "request-a",
            title: "Periodic records"
        });
        expect(authoring.getExchangeInformationRequests).toHaveBeenCalledWith("exchange-a");
    });

    it("reports nothing when the Exchange lists no request or cannot be read", async () => {
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValueOnce(listing([]));
        await expect(findStartedInformationRequest("exchange-a")).resolves.toBeNull();

        vi.mocked(authoring.getExchangeInformationRequests).mockRejectedValueOnce({errorMessage: "Request failed"});
        await expect(findStartedInformationRequest("exchange-a")).resolves.toBeNull();
    });
});
