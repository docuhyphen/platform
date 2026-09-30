/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {
    InformationRequestCapabilitiesDto,
    InformationRequestStandingReason,
    PlanCode,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../../../models/models.tsx";
import {useInformationRequestCapabilities} from "../useInformationRequestCapabilities.ts";
import InformationRequestScopeNotice from "./InformationRequestScopeNotice.tsx";

vi.mock("../useInformationRequestCapabilities.ts", () => ({useInformationRequestCapabilities: vi.fn()}));

const unavailable: InformationRequestCapabilitiesDto = {
    ownerType: SubscriptionOwnerType.USER,
    planCode: PlanCode.FREE,
    subscriptionStatus: SubscriptionStatus.ACTIVE,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
    featureIncluded: false,
    newWorkAvailable: false,
    newWorkUnavailableReason: InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
    operationallySuspended: false,
    typedAnswersAvailable: true,
    personalTemplatesAvailable: false,
    assignedWork: true,
    holdsRequests: false,
};

describe("InformationRequestScopeNotice", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    afterEach(cleanup);

    it("renders nothing until the capabilities are known", () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(null);

        const {container} = render(<InformationRequestScopeNotice idPrefix={"page"}/>);

        expect(container.textContent).toBe("");
    });

    it("announces why new work is unavailable in the active scope", () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(unavailable);

        render(<InformationRequestScopeNotice idPrefix={"page"}/>);

        expect(screen.getByRole("status").textContent).toMatch(/Your plan does not include creating Information Requests/);
        expect(document.getElementById("page-scope-standing")).not.toBeNull();
    });
});
