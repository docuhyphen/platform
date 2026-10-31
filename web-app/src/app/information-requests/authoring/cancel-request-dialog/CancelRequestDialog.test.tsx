/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, within} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import CancelRequestDialog from "./CancelRequestDialog.tsx";

describe("CancelRequestDialog", () => {
    afterEach(cleanup);

    it("cancels with the reason code chosen from the list", async () => {
        const onConfirm = vi.fn();
        render(<CancelRequestDialog busy={false}
                                    onConfirm={onConfirm}
                                    onDismiss={vi.fn()}/>);
        const dialog = await screen.findByRole("dialog", {name: "Cancel this request"});
        const confirm = within(dialog).getByRole("button", {name: "Cancel request"}) as HTMLButtonElement;

        expect(confirm.disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "created-in-error"}});
        fireEvent.click(confirm);

        expect(onConfirm).toHaveBeenCalledWith("created-in-error");
    });

    it("offers only machine reason codes", async () => {
        render(<CancelRequestDialog busy={false}
                                    onConfirm={vi.fn()}
                                    onDismiss={vi.fn()}/>);
        const dialog = await screen.findByRole("dialog", {name: "Cancel this request"});
        const values = Array.from((within(dialog).getByLabelText("Reason") as HTMLSelectElement).options)
            .map(option => option.value)
            .filter(Boolean);

        expect(values.length).toBeGreaterThan(0);
        values.forEach(value => expect(value).toMatch(/^[a-z0-9][a-z0-9._-]{0,127}$/));
    });
});
