import {readFileSync} from "node:fs";
import ts from "typescript";
import {describe, expect, it} from "vitest";

const source = ts.createSourceFile("models.tsx", readFileSync(new URL("../../app/models/models.tsx", import.meta.url), "utf8"), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
const propertyType = (interfaceName: string, propertyName: string): string | undefined =>
    source.statements.filter(ts.isInterfaceDeclaration).find(node => node.name.text === interfaceName)?.members
        .filter(ts.isPropertySignature).find(node => node.name.getText(source) === propertyName)?.type?.getText(source).replace(/\s/g, "");
const enumMembers = (name: string): string[] => source.statements.filter(ts.isEnumDeclaration)
    .filter(node => node.name.text === name).flatMap(node => node.members.map(member => member.name.getText(source)));

describe("shared Information Request wire contracts", () => {
    it.each([
        ["BlueprintDefinitionSummaryDto", "createdAt"], ["BlueprintDefinitionSummaryDto", "updatedAt"],
        ["FieldContractDto", "createdAt"], ["FieldDefinitionDto", "createdAt"],
        ["SchemaVersionDto", "createdAt"], ["SchemaDefinitionDto", "createdAt"], ["SchemaAssignmentDto", "assignedAt"],
    ])("types %s.%s as epoch milliseconds", (interfaceName, field) => {
        expect(propertyType(interfaceName, field)).toBe("number");
    });

    it("preserves nullable publication times as epoch milliseconds", () => {
        expect(propertyType("SchemaVersionDto", "publishedAt")).toBe("number|null");
    });

    it("has one persisted Field operator vocabulary", () => {
        expect(source.statements.filter(ts.isEnumDeclaration).filter(node => node.name.text === "FieldOperator")).toHaveLength(1);
    });

    it("represents every session-visible schema and audit capability and the retained record role", () => {
        expect(enumMembers("Capability")).toEqual(expect.arrayContaining(["FIELD_SCHEMA_READ", "FIELD_SCHEMA_WRITE", "FIELD_SCHEMA_PUBLISH", "AUDIT_ENGAGEMENT_MANAGE"]));
        expect(enumMembers("InformationRequestShareRoleKey")).toContain("RECORD_OWNER");
    });

    it.each(["InformationRequestAccessLinkDto", "InformationRequestAccessLinkIssuedDto"])("keeps the party revision returned by %s", name => {
        expect(propertyType(name, "partyETag")).toBe("string|null");
    });
});
