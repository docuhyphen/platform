/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, useLocation} from "react-router-dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {useInformationRequestCapabilities} from "../../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities,
} from "../../../information-requests/shared/testing/capabilityFixtures.ts";
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
vi.mock("../../../information-requests/capabilities/useInformationRequestCapabilities.ts", () => ({
    useInformationRequestCapabilities: vi.fn(),
}));

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
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
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

    it("stays available to an owner that still holds requests after losing the feature", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: null});
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature({holdsRequests: true}));
        renderNavigation();

        expect(screen.getByLabelText("Information Request operations")).toBeTruthy();
    });

    it("stays hidden for an owner with neither the feature nor requests, or while unknown", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: null});
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature());
        renderNavigation();
        expect(screen.queryByLabelText("Information Request operations")).toBeNull();
        cleanup();

        vi.mocked(useInformationRequestCapabilities).mockReturnValue(null);
        renderNavigation();
        expect(screen.queryByLabelText("Information Request operations")).toBeNull();
    });
});
