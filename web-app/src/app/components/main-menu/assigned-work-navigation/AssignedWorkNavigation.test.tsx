/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    useInformationRequestCapabilities
} from "../../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities,
} from "../../../information-requests/shared/testing/capabilityFixtures.ts";
import AssignedWorkNavigation from "./AssignedWorkNavigation.tsx";

vi.mock("../../../information-requests/capabilities/useInformationRequestCapabilities.ts", () => ({
    useInformationRequestCapabilities: vi.fn(),
}));

const renderNavigation = () => render(
    <MemoryRouter initialEntries={["/exchanges"]}>
        <Routes>
            <Route path={"/exchanges"}
                   element={<AssignedWorkNavigation/>}/>
            <Route path={"/information-request-assignments"}
                   element={<p>Assigned work</p>}/>
        </Routes>
    </MemoryRouter>,
);

const assignedLink = () => screen.queryByRole("button", {name: "Assigned to you"});

describe("AssignedWorkNavigation", () =>
{
    afterEach(cleanup);

    it("opens the work assigned to a caller who holds a party role", async () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature({assignedWork: true}));
        renderNavigation();

        fireEvent.click(assignedLink()!);

        expect(await screen.findByText("Assigned work")).toBeTruthy();
    });

    it("stays hidden while the capabilities are unknown or the caller has no assigned work", () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(null);
        renderNavigation();
        expect(assignedLink()).toBeNull();
        cleanup();

        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities({assignedWork: false}));
        renderNavigation();
        expect(assignedLink()).toBeNull();
    });
});
