/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import PlatformAdministration from "./PlatformAdministration.tsx";

vi.mock("../../utils/useMediaQuery.ts", () => ({
    useIsMobile: () => false,
}));

vi.mock("./organizations/Organizations.tsx", () => ({
    default: () => <div id={"test-platform-organizations"}>Organizations content</div>,
}));

vi.mock("./platform-content/PlatformContent.tsx", () => ({
    default: () => <div id={"test-platform-content"}>Platform Content content</div>,
}));

vi.mock("./app-administrators/AppAdministrators.tsx", () => ({
    default: () => <div id={"test-app-administrators"}>App Administrators content</div>,
}));

vi.mock("./user-subscriptions/UserSubscriptions.tsx", () => ({
    default: () => <div id={"test-user-subscriptions"}>User Subscriptions content</div>,
}));

vi.mock("./trial-requests/TrialRequests.tsx", () => ({
    default: () => <div id={"test-trial-requests"}>Trial Requests content</div>,
}));

afterEach(cleanup);

describe("PlatformAdministration", () =>
{
    it("shows Organizations by default without mounting hidden sections", () =>
    {
        render(<PlatformAdministration/>);

        expect(document.querySelector("#platform-administration-section-header")).toBeNull();
        expect(screen.getByText("Organizations content")).toBeTruthy();
        expect(screen.queryByText("Platform Content content")).toBeNull();
        expect(screen.queryByText("App Administrators content")).toBeNull();
        expect(screen.queryByText("Trial Requests content")).toBeNull();
    });

    it("switches content from the left navigation menu", () =>
    {
        render(<PlatformAdministration/>);

        fireEvent.click(document.querySelector("#platform-administration-content-tab") as HTMLElement);

        expect(screen.getByText("Platform Content content")).toBeTruthy();
        expect(screen.queryByText("Organizations content")).toBeNull();

        fireEvent.click(document.querySelector("#platform-administration-app-administrators-tab") as HTMLElement);

        expect(screen.getByText("App Administrators content")).toBeTruthy();
        expect(screen.queryByText("Platform Content content")).toBeNull();

        fireEvent.click(document.querySelector("#platform-administration-trial-requests-tab") as HTMLElement);

        expect(screen.getByText("Trial Requests content")).toBeTruthy();
        expect(screen.queryByText("App Administrators content")).toBeNull();
    });
});
