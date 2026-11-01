/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {createBlueprint} from "../../../services/blueprintService.ts";
import BlueprintEditorDialog from "./BlueprintEditorDialog.tsx";

vi.mock("../../../services/blueprintService.ts", () => ({createBlueprint: vi.fn(), updateBlueprint: vi.fn()}));
vi.mock("../../../services/variableService.ts", () => ({getAvailableVariables: vi.fn(async () => [])}));
vi.mock("./blueprint-business-fields-tab/BlueprintBusinessFieldsTab.tsx", () => ({default: (): null => null}));
vi.mock("./blueprint-information-request-tab/BlueprintInformationRequestTab.tsx", () => ({default: (): null => null}));
vi.mock("../../exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx", () => ({default: (): null => null}));

describe("saving a Blueprint", () => {
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

    afterEach(cleanup);

    it("shows the reason the server gives for refusing the save", async () => {
        vi.mocked(createBlueprint).mockRejectedValueOnce({
            errorMessage: "Information request template version 2 has been retired; select a currently published version",
            reasonCode: "INFORMATION_REQUEST_TEMPLATE_VERSION_RETIRED",
        });
        render(<BlueprintEditorDialog open={true}
                                      onClose={vi.fn()}
                                      onSaved={vi.fn()}
                                      scope={"ORG"}/>);

        fireEvent.change(document.querySelector("#input-blueprint-name") as HTMLInputElement, {target: {value: "Record collection"}});
        await act(async () => {
            fireEvent.click(screen.getByRole("button", {name: "Create Blueprint"}));
        });

        expect(await screen.findByText(
            "Information request template version 2 has been retired; select a currently published version",
        )).toBeTruthy();
    });
});
