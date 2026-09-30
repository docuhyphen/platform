/** @vitest-environment jsdom */
import {renderHook, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    CurrentSessionDto,
    InformationRequestCapabilitiesDto,
    PlanCode,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../../models/models.tsx";
import {useInformationRequestCapabilities} from "./useInformationRequestCapabilities.ts";

const api = vi.hoisted(() => ({get: vi.fn()}));
const auth = vi.hoisted(() => ({currentSession: null as CurrentSessionDto | null}));

vi.mock("../../../services/informationRequestCapabilityService.ts", () => ({
    getInformationRequestCapabilities: () => api.get(),
}));
vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: auth.currentSession}),
}));

const session = (userId: string, activeOrganizationId: string | null) =>
    ({userId, activeOrganizationId} as CurrentSessionDto);

const capabilities = (ownerType: SubscriptionOwnerType): InformationRequestCapabilitiesDto => ({
    ownerType,
    planCode: ownerType === SubscriptionOwnerType.ORGANIZATION ? PlanCode.BUSINESS : PlanCode.PERSONAL,
    subscriptionStatus: SubscriptionStatus.ACTIVE,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
    featureIncluded: true,
    newWorkAvailable: true,
    operationallySuspended: false,
    typedAnswersAvailable: true,
    personalTemplatesAvailable: true,
    assignedWork: false,
    holdsRequests: false,
});

describe("useInformationRequestCapabilities", () =>
{
    afterEach(() => vi.clearAllMocks());

    it("reads the capabilities once for consumers that mount together", async () =>
    {
        auth.currentSession = session("user-1", null);
        api.get.mockResolvedValue(capabilities(SubscriptionOwnerType.USER));

        const first = renderHook(() => useInformationRequestCapabilities());
        const second = renderHook(() => useInformationRequestCapabilities());

        await waitFor(() => expect(first.result.current?.ownerType).toBe(SubscriptionOwnerType.USER));
        await waitFor(() => expect(second.result.current?.ownerType).toBe(SubscriptionOwnerType.USER));
        expect(api.get).toHaveBeenCalledTimes(1);
    });

    it("reads again for a new active scope and never answers with the previous one", async () =>
    {
        auth.currentSession = session("user-2", null);
        api.get.mockResolvedValueOnce(capabilities(SubscriptionOwnerType.USER));
        const {result, rerender} = renderHook(() => useInformationRequestCapabilities());
        await waitFor(() => expect(result.current?.ownerType).toBe(SubscriptionOwnerType.USER));

        let answer: (value: InformationRequestCapabilitiesDto) => void = () => undefined;
        api.get.mockReturnValueOnce(new Promise<InformationRequestCapabilitiesDto>(resolve =>
        {
            answer = resolve;
        }));
        auth.currentSession = session("user-2", "organization-1");
        rerender();

        expect(result.current).toBeNull();
        answer(capabilities(SubscriptionOwnerType.ORGANIZATION));
        await waitFor(() => expect(result.current?.ownerType).toBe(SubscriptionOwnerType.ORGANIZATION));
        expect(api.get).toHaveBeenCalledTimes(2);
    });

    it("leaves the capabilities unknown when they cannot be read", async () =>
    {
        auth.currentSession = session("user-3", null);
        api.get.mockRejectedValue(new Error("offline"));

        const {result} = renderHook(() => useInformationRequestCapabilities());

        await waitFor(() => expect(api.get).toHaveBeenCalledTimes(1));
        expect(result.current).toBeNull();
    });

    it("reads nothing without a session", () =>
    {
        auth.currentSession = null;

        const {result} = renderHook(() => useInformationRequestCapabilities());

        expect(result.current).toBeNull();
        expect(api.get).not.toHaveBeenCalled();
    });
});
