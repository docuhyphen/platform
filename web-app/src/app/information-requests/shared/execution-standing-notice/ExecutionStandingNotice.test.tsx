/** @vitest-environment jsdom */
import {render, screen} from "@testing-library/react";
import {beforeAll, describe, expect, it, vi} from "vitest";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestStandingReason,
} from "../../../models/models.tsx";
import ExecutionStandingNotice from "./ExecutionStandingNotice.tsx";

describe("ExecutionStandingNotice", () =>
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

    it("renders nothing while the request is active", () =>
    {
        const {container} = render(
            <ExecutionStandingNotice idPrefix={"standing"}
                                     standing={{kind: InformationRequestExecutionStandingKind.ACTIVE}}/>,
        );

        expect(container.textContent).toBe("");
    });

    it("announces a paused request and its reason where one is given", () =>
    {
        render(
            <ExecutionStandingNotice idPrefix={"standing"}
                                     standing={{
                                         kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
                                         reason: InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
                                     }}/>,
        );

        const notice = screen.getByRole("status");
        expect(notice.textContent).toMatch(/Changes to this request are paused/);
        expect(notice.textContent).toMatch(/account is suspended/);
        expect(document.getElementById("standing-execution-standing")).not.toBeNull();
    });
});
