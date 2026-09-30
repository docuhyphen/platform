/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {useInformationRequestCapabilities} from "../../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities,
} from "../../../information-requests/shared/testing/capabilityFixtures.ts";
import ReviewQueueNavigation from "./ReviewQueueNavigation.tsx";

vi.mock("../../../information-requests/capabilities/useInformationRequestCapabilities.ts", () => ({
    useInformationRequestCapabilities: vi.fn(),
}));

const renderNavigation = () => render(
    <MemoryRouter initialEntries={["/exchanges"]}>
        <Routes>
            <Route path={"/exchanges"}
                   element={<ReviewQueueNavigation/>}/>
            <Route path={"/information-request-reviews"}
                   element={<p>Review queue</p>}/>
        </Routes>
    </MemoryRouter>,
);

const reviewsLink = () => screen.queryByRole("button", {name: "Reviews assigned to you"});

describe("ReviewQueueNavigation", () =>
{
    afterEach(cleanup);

    it("opens the reviewer queue for a caller whose active scope includes Information Requests", async () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
        renderNavigation();

        fireEvent.click(reviewsLink()!);

        expect(await screen.findByText("Review queue")).toBeTruthy();
    });

    it("stays available to a caller without the feature who holds assigned work", () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature({assignedWork: true}));
        renderNavigation();

        expect(reviewsLink()).not.toBeNull();
    });

    it("stays hidden while the capabilities are unknown or offer no reviews", () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(null);
        renderNavigation();
        expect(reviewsLink()).toBeNull();
        cleanup();

        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature());
        renderNavigation();
        expect(reviewsLink()).toBeNull();
    });
});
