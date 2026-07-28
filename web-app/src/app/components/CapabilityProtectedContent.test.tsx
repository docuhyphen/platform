/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {Capability} from "../models/models.tsx";
import CapabilityProtectedContent from "./CapabilityProtectedContent.tsx";

const mockHasCapability = vi.fn();
const mockCurrentSession = vi.fn();

vi.mock("../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: mockCurrentSession(),
        hasCapability: mockHasCapability,
    }),
}));

vi.mock("./AuthBootstrapSplash.tsx", () => ({
    default: () => <div id={"auth-bootstrap-splash"}>Loading session</div>,
}));

const renderGuard = () => render(
    <MemoryRouter initialEntries={["/protected"]}>
        <Routes>
            <Route
                path={"/protected"}
                element={
                    <CapabilityProtectedContent
                        capability={Capability.APP_ADMIN}
                        element={<div>Protected content</div>}/>
                }/>
            <Route
                path={"/exchanges"}
                element={<div>Exchanges page</div>}/>
        </Routes>
    </MemoryRouter>,
);

afterEach(() =>
{
    cleanup();
    mockHasCapability.mockReset();
    mockCurrentSession.mockReset();
});

describe("CapabilityProtectedContent", () =>
{
    it("waits for the live session before evaluating capabilities", () =>
    {
        mockCurrentSession.mockReturnValue(null);
        renderGuard();

        expect(document.getElementById("auth-bootstrap-splash")).toBeTruthy();
    });

    it("renders protected content when the session has the capability", () =>
    {
        mockCurrentSession.mockReturnValue({userId: "user-1"});
        mockHasCapability.mockReturnValue(true);
        renderGuard();

        expect(screen.getByText("Protected content")).toBeTruthy();
    });

    it("redirects when the live session lacks the capability", () =>
    {
        mockCurrentSession.mockReturnValue({userId: "user-1"});
        mockHasCapability.mockReturnValue(false);
        renderGuard();

        expect(screen.getByText("Exchanges page")).toBeTruthy();
    });
});
