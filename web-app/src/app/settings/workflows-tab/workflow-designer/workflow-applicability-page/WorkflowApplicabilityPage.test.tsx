/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as fields from "../../../../../services/fieldsService.ts";
import * as templates from "../../../../../services/informationRequestTemplateService.ts";
import WorkflowApplicabilityPage from "./WorkflowApplicabilityPage.tsx";

vi.mock("../../../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: vi.fn(),
    getInformationRequestTemplate: vi.fn(),
}));
vi.mock("../../../../../services/fieldsService.ts", () => ({
    listSchemas: vi.fn(),
    getResolvedSchema: vi.fn(),
}));

describe("WorkflowApplicabilityPage", () => {
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
        vi.clearAllMocks();
        vi.mocked(templates.listInformationRequestTemplates).mockResolvedValue([]);
        vi.mocked(fields.listSchemas).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("offers requirement conditions to an organization workflow whose trigger names a Submission Package", async () => {
        render(<WorkflowApplicabilityPage scope={"ORG"}
                                          triggerKind={"SUBMISSION_PACKAGE"}
                                          onChange={vi.fn()}/>);

        expect(screen.getByLabelText("Template")).toBeTruthy();
        expect(screen.getByText(/whose answers meet every condition/)).toBeTruthy();
        expect(document.getElementById("applicability-schema-select")).toBeNull();
    });

    it("runs a request trigger without a Submission Package for every event", () => {
        render(<WorkflowApplicabilityPage scope={"ORG"}
                                          triggerKind={"REQUEST"}
                                          onChange={vi.fn()}/>);

        expect(screen.getByText(/runs for every event of its Information Request trigger/)).toBeTruthy();
        expect(screen.queryByLabelText("Template")).toBeNull();
    });

    it("keeps Exchange Field conditions for Exchange triggers", () => {
        render(<WorkflowApplicabilityPage scope={"ORG"}
                                          triggerKind={"EXCHANGE"}
                                          onChange={vi.fn()}/>);

        expect(document.getElementById("applicability-schema-select")).not.toBeNull();
        expect(screen.queryByLabelText("Template")).toBeNull();
    });

    it("offers no conditions outside organization workflows", () => {
        render(<WorkflowApplicabilityPage scope={"PERSONAL"}
                                          triggerKind={"SUBMISSION_PACKAGE"}
                                          onChange={vi.fn()}/>);

        expect(screen.getByText("Applicability conditions are available only for organization workflows.")).toBeTruthy();
    });
});
