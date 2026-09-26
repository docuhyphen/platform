/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import ReviewQueueNavigation from "./ReviewQueueNavigation.tsx";

vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

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

describe("ReviewQueueNavigation", () =>
{
    afterEach(cleanup);

    it("opens the reviewer queue only for a caller whose plan includes Information Requests", async () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));
        renderNavigation();
        expect(screen.queryByRole("button", {name: "Reviews assigned to you"})).toBeNull();
        cleanup();

        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
        renderNavigation();
        fireEvent.click(screen.getByRole("button", {name: "Reviews assigned to you"}));
        expect(await screen.findByText("Review queue")).toBeTruthy();
    });
});
