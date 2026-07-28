/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, useLocation} from "react-router-dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {Capability} from "../../../models/models.tsx";
import PlatformNavigation from "./PlatformNavigation.tsx";

const mockHasCapability = vi.fn();

vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        hasCapability: mockHasCapability,
    }),
}));

const CurrentPath = () =>
{
    const location = useLocation();
    return <span>{location.pathname}</span>;
};

const renderNavigation = () => render(
    <MemoryRouter>
        <PlatformNavigation/>
        <CurrentPath/>
    </MemoryRouter>,
);

afterEach(() =>
{
    cleanup();
    mockHasCapability.mockReset();
});

describe("PlatformNavigation", () =>
{
    it("shows both platform destinations to an App Administrator", () =>
    {
        mockHasCapability.mockImplementation((capability: Capability) =>
            capability === Capability.APP_ADMIN || capability === Capability.APP_AUDIT_READ);
        renderNavigation();

        expect(screen.getByLabelText("Platform Administration")).toBeTruthy();
        expect(screen.getByLabelText("Platform Audit")).toBeTruthy();
    });

    it("shows only Platform Audit to an App Auditor", () =>
    {
        mockHasCapability.mockImplementation((capability: Capability) =>
            capability === Capability.APP_AUDIT_READ);
        renderNavigation();

        expect(screen.queryByLabelText("Platform Administration")).toBeNull();
        expect(screen.getByLabelText("Platform Audit")).toBeTruthy();
    });

    it("navigates to the platform administration route", () =>
    {
        mockHasCapability.mockImplementation((capability: Capability) =>
            capability === Capability.APP_ADMIN);
        renderNavigation();

        fireEvent.click(screen.getByLabelText("Platform Administration"));

        expect(screen.getByText("/platform/administration")).toBeTruthy();
    });
});
