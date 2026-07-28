/** @vitest-environment jsdom */
import {cleanup, render, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import CommunicationPickerDialog from "./CommunicationPickerDialog.tsx";

const listCommunications = vi.fn();

vi.mock("../../../services/communicationService.ts", () => ({
    listCommunications: (...args: unknown[]) => listCommunications(...args),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("CommunicationPickerDialog", () =>
{
    it("uses only the fixed platform scope in Platform Administration", async () =>
    {
        listCommunications.mockResolvedValue([]);

        const {queryByText} = render(
            <CommunicationPickerDialog
                open={true}
                fixedScope={"PLATFORM"}
                onClose={vi.fn()}
                onSelect={vi.fn()}/>,
        );

        await waitFor(() =>
            expect(listCommunications).toHaveBeenCalledWith({scope: "PLATFORM"}),
        );
        expect(queryByText("My Communications")).toBeNull();
        expect(queryByText("Organization")).toBeNull();
        expect(listCommunications).toHaveBeenCalledTimes(1);
    });
});
