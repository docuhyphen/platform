/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import ResponseSectionNavigation from "./ResponseSectionNavigation.tsx";

describe("ResponseSectionNavigation", () => {
    beforeAll(() => {
        Element.prototype.scrollIntoView = vi.fn();
    });

    afterEach(cleanup);

    it("opens the confirmation card of a section that holds only a confirmation and says where to confirm", () => {
        render(
            <>
                <ResponseSectionNavigation summaries={[{
                    id: "section-b",
                    title: "Confirmation",
                    answered: 0,
                    required: 0,
                    confirmations: 1,
                    occurrencePath: "root",
                    requirementKey: "confirm-all-key",
                    requirementId: "runtime-confirm-all",
                }]}/>
                <article id={"information-request-attestation-runtime-confirm-all"}
                         aria-label={"Confirm the response"}/>
            </>,
        );

        const open = screen.getByRole("button", {name: "Confirmation, Confirm under Review and submit"});
        fireEvent.click(open);

        expect(document.activeElement).toBe(document.getElementById("information-request-attestation-runtime-confirm-all"));
    });
});
