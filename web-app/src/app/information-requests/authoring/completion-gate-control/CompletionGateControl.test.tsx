/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import CompletionGateControl from "./CompletionGateControl.tsx";

const GATE = "Must finish before the Exchange can end";

describe("CompletionGateControl", () => {
    afterEach(cleanup);

    it("lets a manager stop the Exchange from waiting for the request", () => {
        const onChange = vi.fn();
        render(<CompletionGateControl gatesExchangeClosure={true}
                                      editable={true}
                                      busy={false}
                                      onChange={onChange}/>);

        expect(screen.getByRole("region", {name: "Ending the Exchange"})).toBeTruthy();
        const gate = screen.getByRole("switch", {name: GATE}) as HTMLInputElement;
        expect(gate.checked).toBe(true);
        fireEvent.click(gate);

        expect(onChange).toHaveBeenCalledWith(false);
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("lets a manager make the Exchange wait again", () => {
        const onChange = vi.fn();
        render(<CompletionGateControl gatesExchangeClosure={false}
                                      editable={true}
                                      busy={false}
                                      onChange={onChange}/>);

        fireEvent.click(screen.getByRole("switch", {name: GATE}));

        expect(onChange).toHaveBeenCalledWith(true);
    });

    it("only states the setting once the request can no longer change", () => {
        render(<CompletionGateControl gatesExchangeClosure={false}
                                      editable={false}
                                      busy={false}
                                      onChange={vi.fn()}/>);

        expect(screen.queryByRole("switch")).toBeNull();
        expect(screen.getByText(/can end without waiting for this request/)).toBeTruthy();
    });
});
