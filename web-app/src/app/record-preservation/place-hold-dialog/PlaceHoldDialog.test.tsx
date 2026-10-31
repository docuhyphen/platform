/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as records from "../../../services/recordPreservationService.ts";
import PlaceHoldDialog from "./PlaceHoldDialog.tsx";

vi.mock("../../../services/recordPreservationService.ts", () => ({placeRecordPreservationHold: vi.fn()}));

const renderDialog = () => render(<PlaceHoldDialog resourceType={"INFORMATION_REQUEST"}
                                                   resourceId={"request-a"}
                                                   onPlaced={vi.fn()}
                                                   onDismiss={vi.fn()}/>);

describe("PlaceHoldDialog", () => {
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

    beforeEach(() => vi.clearAllMocks());

    afterEach(cleanup);

    it("is named by its title", () => {
        renderDialog();

        expect(screen.getByRole("dialog", {name: "Place a preservation hold"})).toBeTruthy();
    });

    it("caps the case reference at the 256 characters a hold keeps and the reason at 2048", () => {
        renderDialog();

        const dialog = within(screen.getByRole("dialog"));
        expect((dialog.getByRole("textbox", {name: "Case reference"}) as HTMLInputElement).maxLength).toBe(256);
        expect((dialog.getByRole("textbox", {name: /Reason/}) as HTMLTextAreaElement).maxLength).toBe(2048);
    });

    it("announces a refused placement and keeps the dialog open with every part identified", async () => {
        vi.mocked(records.placeRecordPreservationHold).mockRejectedValue({errorMessage: "A case reference is at most 256 characters"});
        renderDialog();
        const dialog = within(screen.getByRole("dialog", {name: "Place a preservation hold"}));

        fireEvent.change(dialog.getByRole("textbox", {name: /Reason/}), {target: {value: "Pending review"}});
        fireEvent.click(dialog.getByRole("button", {name: "Place hold"}));

        expect((await dialog.findByRole("alert")).textContent).toBe("A case reference is at most 256 characters");
        await waitFor(() => expect(dialog.getByRole("button", {name: "Place hold"}).hasAttribute("disabled")).toBe(false));
        expect(document.querySelectorAll(".fui-DialogBody:not([id]), .fui-MessageBarBody:not([id])")).toHaveLength(0);
    });
});
