/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {InformationRequestResponseDisposition} from "../../../models/models.tsx";
import ResponseDispositionChoice from "./ResponseDispositionChoice.tsx";

const PHONE_WIDTH = 360;

describe("ResponseDispositionChoice", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    afterEach(() => {
        cleanup();
        vi.unstubAllGlobals();
    });

    it("lets its choices wrap onto further lines so none is clipped at phone width", () => {
        vi.stubGlobal("innerWidth", PHONE_WIDTH);
        render(
            <>
                <span id={"prompt-a"}>Describe the records kept this period</span>
                <ResponseDispositionChoice elementId={"root-records"}
                                           promptId={"prompt-a"}
                                           value={InformationRequestResponseDisposition.PROVIDED}
                                           options={[
                                               InformationRequestResponseDisposition.PROVIDED,
                                               InformationRequestResponseDisposition.NOT_APPLICABLE,
                                               InformationRequestResponseDisposition.UNAVAILABLE,
                                               InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
                                           ]}
                                           onChange={vi.fn()}/>
            </>,
        );

        const group = screen.getByRole("radiogroup", {name: "Your answer Describe the records kept this period"});
        expect(window.getComputedStyle(group).display).toBe("flex");
        expect(window.getComputedStyle(group).flexWrap).toBe("wrap");
        expect(window.getComputedStyle(group).minWidth).toBe("0");
        expect(screen.getAllByRole("radio")).toHaveLength(4);
    });
});
