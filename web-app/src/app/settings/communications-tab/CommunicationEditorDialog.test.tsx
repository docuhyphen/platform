/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import CommunicationEditorDialog from "./CommunicationEditorDialog.tsx";

const createCommunication = vi.fn();
const getAvailableVariables = vi.fn();

vi.mock("../../../services/communicationService.ts", () => ({
    createCommunication: (...args: unknown[]) => createCommunication(...args),
    previewCommunication: vi.fn(),
    updateCommunication: vi.fn(),
}));

vi.mock("../../../services/variableService.ts", () => ({
    getAvailableVariables: (...args: unknown[]) => getAvailableVariables(...args),
}));

afterEach(() =>
{
    cleanup();
    vi.clearAllMocks();
});

describe("CommunicationEditorDialog", () =>
{
    it("creates with fixed PLATFORM scope without requesting tenant variables", async () =>
    {
        createCommunication.mockResolvedValue({});
        render(
            <CommunicationEditorDialog
                open={true}
                scope={"PLATFORM"}
                enforcedScope={"PLATFORM"}
                createAsTemplate={true}
                onClose={vi.fn()}
                onSaved={vi.fn()}/>,
        );

        fireEvent.change(document.querySelector("#input-comm-name") as HTMLInputElement, {
            target: {value: "Platform notice"},
        });
        fireEvent.change(document.querySelector("#input-comm-subject") as HTMLInputElement, {
            target: {value: "Notice subject"},
        });
        fireEvent.change(document.querySelector("#textarea-comm-body") as HTMLTextAreaElement, {
            target: {value: "Notice body"},
        });
        fireEvent.click(document.querySelector("#button-comm-save") as HTMLButtonElement);

        await waitFor(() => expect(createCommunication).toHaveBeenCalledWith(
            expect.objectContaining({
                scope: "PLATFORM",
                isTemplate: true,
            }),
        ));
        expect(getAvailableVariables).not.toHaveBeenCalled();
    });
});
