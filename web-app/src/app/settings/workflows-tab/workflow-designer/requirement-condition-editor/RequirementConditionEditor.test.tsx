/** @vitest-environment jsdom */
import {useState} from "react";
import {cleanup, fireEvent, render, screen, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as fields from "../../../../../services/fieldsService.ts";
import * as templates from "../../../../../services/informationRequestTemplateService.ts";
import {
    FieldOperator,
    FieldScopeKind,
    FieldValueType,
    InformationRequestTemplateScopeKind,
    WorkflowApplicabilityDraft,
} from "../../../../models/models.tsx";
import {unnamedControls} from "../../../../information-requests/shared/testing/unnamedControls.ts";
import RequirementConditionEditor from "./RequirementConditionEditor.tsx";
import {requestSchema, template, templateSummary} from "./requirementConditionTestFixtures.ts";

vi.mock("../../../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: vi.fn(),
    getInformationRequestTemplate: vi.fn(),
}));
vi.mock("../../../../../services/fieldsService.ts", () => ({listSchemas: vi.fn()}));

const renderEditor = (initial?: WorkflowApplicabilityDraft) => {
    const changes = vi.fn();
    const Harness = () => {
        const [applicability, setApplicability] = useState(initial);
        return (
            <RequirementConditionEditor applicability={applicability}
                                        onChange={next => {
                                            changes(next);
                                            setApplicability(next);
                                        }}/>
        );
    };
    render(<Harness/>);
    return changes;
};

const chooseTemplate = async () => {
    await screen.findByRole("option", {name: "Periodic records"});
    fireEvent.change(screen.getByLabelText("Template"), {target: {value: "template-a"}});
    await screen.findByRole("option", {name: "Requested amount (amount)"});
};

const addCondition = (templateRequirementId: string) =>
    fireEvent.change(screen.getByLabelText("Add condition"), {target: {value: templateRequirementId}});

const row = (index: number): HTMLElement => document.getElementById(`requirement-condition-${index}`) as HTMLElement;

describe("RequirementConditionEditor", () => {
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
        vi.mocked(templates.listInformationRequestTemplates).mockResolvedValue([templateSummary]);
        vi.mocked(templates.getInformationRequestTemplate).mockResolvedValue(template);
        vi.mocked(fields.listSchemas).mockImplementation(async params =>
            params?.scopeKind === FieldScopeKind.ORGANIZATION ? [requestSchema] : []);
    });

    afterEach(cleanup);

    it("adds a condition on a typed answer of an organization Template", async () => {
        const changes = renderEditor();
        await chooseTemplate();

        expect(screen.queryByRole("option", {name: /Supporting file/})).toBeNull();
        addCondition("requirement-amount");

        expect(changes).toHaveBeenLastCalledWith({
            fieldConditions: [],
            requirementConditions: [{
                templateRequirementId: "requirement-amount",
                occurrencePath: "root",
                requirementKey: "amount",
                valueType: FieldValueType.DECIMAL,
                operator: FieldOperator.EQUALS,
                value: undefined,
            }],
        });
        expect(within(row(0)).getByText("Requested amount (amount)")).toBeTruthy();
        expect(templates.listInformationRequestTemplates)
            .toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION});
    });

    it("compares an answer with the operators and values its Field type offers", async () => {
        const changes = renderEditor();
        await chooseTemplate();
        addCondition("requirement-category");

        fireEvent.change(within(row(0)).getByLabelText("Condition"), {target: {value: FieldOperator.NOT_EQUALS}});
        fireEvent.change(within(row(0)).getByLabelText("Value"), {target: {value: "priority"}});
        expect(changes.mock.lastCall?.[0].requirementConditions[0]).toEqual(expect.objectContaining({
            templateRequirementId: "requirement-category",
            valueType: FieldValueType.SINGLE_SELECT,
            operator: FieldOperator.NOT_EQUALS,
            value: "priority",
        }));

        addCondition("requirement-amount");
        fireEvent.change(within(row(1)).getByLabelText("Value"), {target: {value: "120.50"}});
        expect(changes.mock.lastCall?.[0].requirementConditions[1]).toEqual(expect.objectContaining({value: "120.50"}));

        fireEvent.change(within(row(1)).getByLabelText("Condition"), {target: {value: FieldOperator.IS_EMPTY}});
        expect(changes.mock.lastCall?.[0].requirementConditions[1]).toEqual(expect.objectContaining({
            operator: FieldOperator.IS_EMPTY,
            value: undefined,
        }));
        expect(within(row(1)).getByText("No value needed")).toBeTruthy();
    });

    it("anchors a repeated answer to its first occurrence and lets the author name another", async () => {
        const changes = renderEditor();
        await chooseTemplate();
        addCondition("requirement-line-total");

        expect(changes.mock.lastCall?.[0].requirementConditions[0].occurrencePath).toBe("lines[0]/items[0]");
        fireEvent.change(within(row(0)).getByLabelText(/Occurrence/), {target: {value: "lines[1]/items[0]"}});

        expect(changes.mock.lastCall?.[0].requirementConditions[0].occurrencePath).toBe("lines[1]/items[0]");
    });

    it("keeps a stored condition it cannot resolve unchanged until the author removes it", async () => {
        const changes = renderEditor({
            fieldConditions: [],
            requirementConditions: [{
                templateRequirementId: "requirement-stored",
                occurrencePath: "root",
                requirementKey: "stored-answer",
                valueType: FieldValueType.BOOLEAN,
                operator: FieldOperator.EQUALS,
                value: true,
            }],
        });

        expect(within(row(0)).getByText("stored-answer")).toBeTruthy();
        await screen.findByRole("option", {name: "Periodic records"});
        expect(changes).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole("button", {name: "Remove the condition on stored-answer"}));

        expect(changes).toHaveBeenLastCalledWith(undefined);
    });

    it("states a failed read of the organization Templates and lists them once Try again reads them", async () => {
        vi.mocked(templates.listInformationRequestTemplates)
            .mockRejectedValueOnce({errorMessage: "Request failed"})
            .mockResolvedValueOnce([templateSummary]);
        renderEditor();

        expect(await screen.findByText("The organization Templates could not be loaded.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(screen.queryByText("The organization Templates could not be loaded.")).toBeNull();
    });

    it("states a failed read of a Template's answers instead of offering none", async () => {
        vi.mocked(templates.getInformationRequestTemplate)
            .mockRejectedValueOnce({errorMessage: "Request failed"})
            .mockResolvedValueOnce(template);
        renderEditor();
        await screen.findByRole("option", {name: "Periodic records"});
        fireEvent.change(screen.getByLabelText("Template"), {target: {value: "template-a"}});

        expect(await screen.findByText("The typed answers of this Template could not be loaded.")).toBeTruthy();
        expect(screen.queryByText("No typed answer left to add")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByRole("option", {name: "Requested amount (amount)"})).toBeTruthy();
    });

    it("reopens stored conditions with the Template that holds them", async () => {
        renderEditor({
            fieldConditions: [],
            requirementConditions: [{
                templateRequirementId: "requirement-category",
                occurrencePath: "root",
                requirementKey: "category",
                valueType: FieldValueType.SINGLE_SELECT,
                operator: FieldOperator.EQUALS,
                value: "priority",
            }],
        });

        expect(await within(row(0)).findByText("Category (category)")).toBeTruthy();
        expect((screen.getByLabelText("Template") as HTMLSelectElement).value).toBe("template-a");
    });

    it("names every control of the editor and its conditions", async () => {
        renderEditor();
        await chooseTemplate();
        addCondition("requirement-category");
        addCondition("requirement-line-total");

        expect(unnamedControls(document.body)).toEqual([]);
    });
});
