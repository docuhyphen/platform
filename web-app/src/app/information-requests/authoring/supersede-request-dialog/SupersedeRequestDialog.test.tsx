/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {InformationRequestState, InformationRequestSummaryDto} from "../../../models/models.tsx";
import SupersedeRequestDialog from "./SupersedeRequestDialog.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getExchangeInformationRequests: vi.fn(),
}));

type Listing = Awaited<ReturnType<typeof authoring.getExchangeInformationRequests>>;

const summary = (id: string, title: string, state: InformationRequestState, canManage = true) =>
    ({id, title, state, permissions: {canManage, canRespond: false, canReview: false}}) as InformationRequestSummaryDto;

const listing = (...requests: InformationRequestSummaryDto[]) => ({requests, canCreate: true}) as Listing;

const renderDialog = (onConfirm = vi.fn()) => {
    render(<SupersedeRequestDialog requestId={"request-a"}
                                   exchangeId={"exchange-a"}
                                   busy={false}
                                   onConfirm={onConfirm}
                                   onDismiss={vi.fn()}/>);
    return onConfirm;
};

describe("SupersedeRequestDialog", () => {
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

    beforeEach(() => {
        vi.mocked(authoring.getExchangeInformationRequests).mockReset();
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue(listing(
            summary("request-a", "Current request", InformationRequestState.ISSUED),
            summary("request-b", "Replacement request", InformationRequestState.DRAFT),
        ));
    });

    afterEach(cleanup);

    it("supersedes with the chosen replacement and reason code", async () => {
        const onConfirm = renderDialog();
        const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});
        await waitFor(() => expect(within(dialog).getByRole("option", {name: "Replacement request"})).toBeTruthy());
        const confirm = within(dialog).getByRole("button", {name: "Supersede"}) as HTMLButtonElement;

        fireEvent.change(within(dialog).getByLabelText("Replacement request"), {target: {value: "request-b"}});
        expect(confirm.disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "requirements-changed"}});
        fireEvent.click(confirm);

        expect(onConfirm).toHaveBeenCalledWith("request-b", "requirements-changed");
    });

    it("offers only open requests the caller manages as the replacement", async () => {
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue(listing(
            summary("request-a", "Current request", InformationRequestState.ISSUED),
            summary("request-b", "Managed draft", InformationRequestState.DRAFT),
            summary("request-c", "Viewed draft", InformationRequestState.DRAFT, false),
            summary("request-d", "Closed request", InformationRequestState.CLOSED),
        ));
        renderDialog();
        const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});

        await waitFor(() => expect(within(dialog).getByRole("option", {name: "Managed draft"})).toBeTruthy());
        expect(within(dialog).queryByRole("option", {name: "Viewed draft"})).toBeNull();
        expect(within(dialog).queryByRole("option", {name: "Closed request"})).toBeNull();
        expect(within(dialog).queryByRole("option", {name: "Current request"})).toBeNull();
    });

    it("states a refused read of the candidates with Try again, offers no replacement meanwhile, and lists them once a retry loads", async () => {
        vi.mocked(authoring.getExchangeInformationRequests)
            .mockRejectedValueOnce({errorMessage: "Replacement candidates refused"})
            .mockResolvedValueOnce(listing(summary("request-b", "Replacement request", InformationRequestState.DRAFT)));
        renderDialog();
        const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});

        expect(await within(dialog).findByText("Replacement candidates refused")).toBeTruthy();
        expect(within(dialog).queryByText(/Create the replacement request/)).toBeNull();
        expect(within(dialog).queryByLabelText("Replacement request")).toBeNull();
        expect((within(dialog).getByRole("button", {name: "Supersede"}) as HTMLButtonElement).disabled).toBe(true);

        fireEvent.click(within(dialog).getByRole("button", {name: "Try again"}));

        expect(await within(dialog).findByRole("option", {name: "Replacement request"})).toBeTruthy();
        expect(within(dialog).queryByText("Replacement candidates refused")).toBeNull();
    });

    it("says how to proceed when the Exchange holds no request that can take over", async () => {
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue(listing(
            summary("request-a", "Current request", InformationRequestState.ISSUED),
        ));
        renderDialog();
        const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});

        expect(await within(dialog).findByText(/Create the replacement request first/)).toBeTruthy();
        expect(within(dialog).queryByRole("button", {name: "Try again"})).toBeNull();
    });
});
