/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    FieldOperator,
    FieldValueType,
    InformationRequestTemplateConditionPredicateRequest,
} from "../../../models/models.tsx";
import {toCanonicalDateTime} from "../../../exchanges/components/exchange-fields-tab/fieldDateTimeCanonical.ts";
import ConditionPredicateRow, {PredicateSource} from "./ConditionPredicateRow.tsx";

const OPTIONS = [{code: "first-tier", label: "First tier"}, {code: "second-tier", label: "Second tier"}];

const source = (valueType: FieldValueType): PredicateSource => ({
    value: "field:field-definition-1",
    label: "Value of Tier",
    valueType,
    options: valueType === FieldValueType.SINGLE_SELECT || valueType === FieldValueType.MULTI_SELECT ? OPTIONS : [],
});

const predicate = (
    valueType: FieldValueType,
    operator: FieldOperator,
    value?: unknown,
): InformationRequestTemplateConditionPredicateRequest => ({
    operator,
    fieldDefinitionId: "field-definition-1",
    valueType,
    value
});

const renderRow = (row: InformationRequestTemplateConditionPredicateRequest) => {
    const onChange = vi.fn();
    render(
        <ConditionPredicateRow id={"predicate-0"}
                               predicate={row}
                               sources={[source(row.valueType as FieldValueType)]}
                               readOnly={false}
                               onChange={onChange}
                               onRemove={vi.fn()}/>,
    );
    return onChange;
};

const valueSelect = () => document.querySelector("#predicate-0-value") as HTMLSelectElement;

describe("a Template condition test takes its value in the form its Field holds", () => {
    afterEach(cleanup);

    it("compares a Boolean Field with a Yes or No choice", () => {
        const onChange = renderRow(predicate(FieldValueType.BOOLEAN, FieldOperator.EQUALS));

        fireEvent.change(valueSelect(), {target: {value: "true"}});
        fireEvent.change(valueSelect(), {target: {value: "false"}});

        expect(screen.getByRole("option", {name: "Yes"})).toBeTruthy();
        expect(onChange.mock.calls.map(call => call[0].value)).toEqual([true, false]);
    });

    it("shows a stored Boolean value as the choice it means", () => {
        renderRow(predicate(FieldValueType.BOOLEAN, FieldOperator.EQUALS, true));

        expect(valueSelect().value).toBe("true");
    });

    it("offers the Field's options by label and stores the chosen option's code", () => {
        const onChange = renderRow(predicate(FieldValueType.SINGLE_SELECT, FieldOperator.EQUALS));

        fireEvent.change(valueSelect(), {target: {value: "second-tier"}});

        expect(screen.getByRole("option", {name: "Second tier"})).toBeTruthy();
        expect(onChange.mock.calls[0][0].value).toBe("second-tier");
    });

    it("picks several of the Field's options for a test of one of several values", () => {
        const onChange = renderRow(predicate(FieldValueType.SINGLE_SELECT, FieldOperator.IN, ["first-tier"]));

        fireEvent.click(screen.getByRole("checkbox", {name: "Second tier"}));

        expect(onChange.mock.calls[0][0].value).toEqual(["first-tier", "second-tier"]);
    });

    it("keeps the spaces typed inside a text value", () => {
        const onChange = renderRow(predicate(FieldValueType.SHORT_TEXT, FieldOperator.EQUALS, "two "));

        fireEvent.change(document.querySelector("#predicate-0-value") as HTMLInputElement, {target: {value: "two words"}});

        expect(onChange.mock.calls[0][0].value).toBe("two words");
    });

    it("takes a date-time in the form Fields store it", () => {
        const onChange = renderRow(predicate(FieldValueType.DATE_TIME, FieldOperator.GREATER_THAN));
        const input = document.querySelector("#predicate-0-value") as HTMLInputElement;

        fireEvent.change(input, {target: {value: "2026-10-26T09:30"}});

        expect(input.type).toBe("datetime-local");
        expect(onChange.mock.calls[0][0].value).toBe(toCanonicalDateTime("2026-10-26T09:30"));
    });

    it("takes a date from a date input and says how to write a number", () => {
        renderRow(predicate(FieldValueType.DATE, FieldOperator.LESS_THAN));
        expect((document.querySelector("#predicate-0-value") as HTMLInputElement).type).toBe("date");
        cleanup();

        renderRow(predicate(FieldValueType.DECIMAL, FieldOperator.LESS_THAN));
        expect(screen.getByText("A number, for example 12 or 4.50")).toBeTruthy();
    });
});
