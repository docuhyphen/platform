/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, useLocation} from "react-router-dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {Capability} from "../../../models/models.tsx";
import OperationsNavigation from "./OperationsNavigation.tsx";

const mockHasCapability = vi.fn();
const mockSession = vi.fn();

vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: mockSession(),
        hasCapability: mockHasCapability,
    }),
}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

const CurrentPath = () =>
{
    const location = useLocation();
    return <span>{location.pathname}</span>;
};

const renderNavigation = () => render(
    <MemoryRouter>
        <OperationsNavigation/>
        <CurrentPath/>
    </MemoryRouter>,
);

describe("OperationsNavigation", () =>
{
    beforeEach(() =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
        mockHasCapability.mockReturnValue(false);
    });

    afterEach(() =>
    {
        cleanup();
        vi.clearAllMocks();
    });

    it("opens the operations queue for a personal owner", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: null});
        renderNavigation();

        fireEvent.click(screen.getByLabelText("Information Request operations"));

        expect(screen.getByText("/information-request-operations")).toBeTruthy();
    });

    it("shows inside an organization only to members who may read its operations", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: "organization-a"});
        renderNavigation();
        expect(screen.queryByLabelText("Information Request operations")).toBeNull();
        cleanup();

        mockHasCapability.mockImplementation((capability: Capability) =>
            capability === Capability.INFORMATION_REQUEST_OPERATIONS_READ);
        renderNavigation();
        expect(screen.getByLabelText("Information Request operations")).toBeTruthy();
    });

    it("stays hidden without the plan feature", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: null});
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));
        renderNavigation();

        expect(screen.queryByLabelText("Information Request operations")).toBeNull();
    });
});
