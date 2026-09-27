/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as exchangeApi from "../../../../services/exchangeApi.ts";
import {ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import ExchangeEndDialog from "./ExchangeEndDialog.tsx";

vi.mock("../../../../services/exchangeApi.ts", () => ({updateExchange: vi.fn()}));
vi.mock("../../../observable/exchangeObservables.ts", () => ({publishExchangeUpdate: vi.fn()}));

const exchange = {id: "exchange-a", name: "Quarterly records"} as ExchangeDetailedDto;

const renderDialog = (onExchangeEnded = vi.fn()) => render(
    <ExchangeEndDialog isOpen={true}
                       onDismiss={vi.fn()}
                       exchange={exchange}
                       onExchangeEnded={onExchangeEnded}/>,
);

describe("ExchangeEndDialog", () =>
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

    beforeEach(() => vi.clearAllMocks());

    afterEach(cleanup);

    it("offers to cancel the open Information Requests when ending requires it", async () =>
    {
        const onExchangeEnded = vi.fn();
        vi.mocked(exchangeApi.updateExchange)
            .mockRejectedValueOnce({
                errorMessage: "This Exchange cannot end while its Information Requests are open",
                reasonCode: "INFORMATION_REQUEST_REMAINING_REQUESTS_REQUIRE_CANCELLATION",
                informationRequestIds: ["request-a", "request-b"],
            })
            .mockResolvedValueOnce({...exchange, status: ExchangeStatus.ENDED});
        renderDialog(onExchangeEnded);

        fireEvent.click(screen.getByRole("button", {name: "End Exchange"}));
        expect(await screen.findByText("2 Information Requests on this Exchange are still open. Ending the Exchange now cancels them.")).toBeTruthy();
        expect(exchangeApi.updateExchange).toHaveBeenLastCalledWith("exchange-a", {
            status: ExchangeStatus.ENDED,
            cancelRemainingInformationRequests: undefined,
        });

        fireEvent.click(screen.getByRole("button", {name: "Cancel requests and end"}));
        await waitFor(() => expect(exchangeApi.updateExchange).toHaveBeenLastCalledWith("exchange-a", {
            status: ExchangeStatus.ENDED,
            cancelRemainingInformationRequests: true,
        }));
        await waitFor(() => expect(onExchangeEnded).toHaveBeenCalled());
    });

    it("keeps the Exchange open while requests that must finish first are incomplete", async () =>
    {
        vi.mocked(exchangeApi.updateExchange).mockRejectedValueOnce({
            errorMessage: "This Exchange cannot end while its Information Requests are open",
            reasonCode: "INFORMATION_REQUEST_COMPLETION_GATES_UNSATISFIED",
            informationRequestIds: ["request-a"],
        });
        renderDialog();

        fireEvent.click(screen.getByRole("button", {name: "End Exchange"}));

        expect(await screen.findByText("This Exchange cannot end until 1 Information Requests that must finish first are complete.")).toBeTruthy();
        expect(screen.getByRole("button", {name: "End Exchange"}).hasAttribute("disabled")).toBe(true);
    });
});
