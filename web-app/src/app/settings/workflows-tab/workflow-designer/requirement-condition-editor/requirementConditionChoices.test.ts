import {describe, expect, it} from "vitest";
import {FieldOperator, FieldValueType, InformationRequestTemplateGroupDto, SchemaDefinitionDto, SchemaVersionDto} from "../../../../models/models.tsx";
import {defaultRequirementCondition, requirementChoicesOf} from "./requirementConditionChoices.ts";
import {requestSchema, template} from "./requirementConditionTestFixtures.ts";

describe("requirementConditionChoices", () => {
    it("offers the typed answers of a Template Version whose Field type it can read", () => {
        const choices = requirementChoicesOf(template.latestPublishedVersion, [requestSchema]);

        expect(choices.map(choice => choice.requirementKey)).toEqual(["amount", "category", "line-total"]);
        expect(choices[1].binding.options.map(option => option.code)).toEqual(["standard", "priority"]);
    });

    it("anchors an answer outside any repeated group to the root and a repeated one to its first occurrence", () => {
        const choices = requirementChoicesOf(template.latestPublishedVersion, [requestSchema]);

        expect(choices[0].occurrencePath).toBe("root");
        expect(choices[0].repeated).toBe(false);
        expect(choices[2].occurrencePath).toBe("lines[0]/items[0]");
        expect(choices[2].repeated).toBe(true);
    });

    it("anchors a repeated answer at its top group when the server sends that group's parent as null", () => {
        const version = template.latestPublishedVersion;
        const sent = version && {
            ...version,
            groups: version.groups.map((group): InformationRequestTemplateGroupDto => ({
                ...group,
                parentGroupKey: group.parentGroupKey ?? null,
                maxOccurrences: null
            })),
        };

        const choices = requirementChoicesOf(sent, [requestSchema]);

        expect(choices.map(choice => choice.occurrencePath)).toEqual(["root", "root", "lines[0]/items[0]"]);
    });

    it("offers the typed answers when a schema the server sends has no draft or no published version", () => {
        const published = (requestSchema as unknown as {
            latestPublishedVersion: SchemaVersionDto
        }).latestPublishedVersion;
        const unpublished = {
            ...requestSchema,
            id: "schema-b",
            latestPublishedVersion: null as SchemaVersionDto | null,
            draftVersion: {...published, id: "schema-version-b"}
        };
        const undrafted = {...requestSchema, draftVersion: null as SchemaVersionDto | null};

        const choices = requirementChoicesOf(template.latestPublishedVersion, [unpublished, undrafted] as unknown as SchemaDefinitionDto[]);

        expect(choices.map(choice => choice.requirementKey)).toEqual(["amount", "category", "line-total"]);
    });

    it("offers nothing it cannot type", () => {
        expect(requirementChoicesOf(template.latestPublishedVersion, [])).toEqual([]);
        expect(requirementChoicesOf(undefined, [requestSchema])).toEqual([]);
    });

    it("starts a condition with the first operator the Field type offers and no value", () => {
        const [amount] = requirementChoicesOf(template.latestPublishedVersion, [requestSchema]);

        expect(defaultRequirementCondition(amount)).toEqual({
            templateRequirementId: "requirement-amount",
            occurrencePath: "root",
            requirementKey: "amount",
            valueType: FieldValueType.DECIMAL,
            operator: FieldOperator.EQUALS,
            value: undefined,
        });
    });
});
