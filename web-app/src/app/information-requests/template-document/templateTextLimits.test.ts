import {describe, expect, it} from "vitest";
import {
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestRequirementType,
} from "../../models/models.tsx";
import {emptyDraftDocument, newRequirement, TemplateDraftDocument} from "./templateDraftDocument.ts";
import {templateDraftProblems} from "./templateDraftValidation.ts";

const long = (length: number) => "a".repeat(length);

const documentWith = (overrides: Partial<TemplateDraftDocument>): TemplateDraftDocument => ({
    ...emptyDraftDocument(),
    ...overrides,
});

const document = (requirement = newRequirement(InformationRequestRequirementType.DOCUMENT, "supporting-record", "Provide the record")) =>
    documentWith({sections: [{sectionKey: "supporting-data", title: "Supporting data", requirements: [requirement]}]});

describe("templateTextLimits", () => {
    it("names text longer than the record keeps and points at where it is", () => {
        const base = document();
        const requirement = base.sections[0].requirements[0];
        const tooLong = documentWith({
            sections: [{
                sectionKey: "supporting-data",
                title: long(256),
                helpText: long(2049),
                requirements: [{
                    ...requirement,
                    prompt: long(1025),
                    helpText: long(2049),
                    evidencePolicy: {
                        minimumFileCount: 1,
                        issuerRequirement: InformationRequestEvidenceAttributeRequirement.REQUIRED,
                        acceptedValues: [{
                            attribute: InformationRequestEvidenceAttribute.ISSUER,
                            acceptedValue: long(256)
                        }],
                    },
                }],
            }],
        });

        const problems = templateDraftProblems(tooLong);
        const messages = problems.map(problem => problem.message);

        expect(messages).toContain("Section \"supporting-data\" has a title longer than 255 characters.");
        expect(messages).toContain("Section \"supporting-data\" has help text longer than 2048 characters.");
        expect(messages).toContain("Requirement \"supporting-record\" has a prompt longer than 1024 characters.");
        expect(messages).toContain("Requirement \"supporting-record\" has help text longer than 2048 characters.");
        expect(messages).toContain("Requirement \"supporting-record\" accepts a value longer than 255 characters.");
        expect(problems.find(problem => problem.message.includes("a prompt longer"))?.target)
            .toEqual({panel: "sections", sectionIndex: 0, requirementIndex: 0});
    });

    it("accepts text at the length the record keeps", () => {
        const base = document();
        const atLimit = documentWith({
            sections: [{
                sectionKey: "supporting-data",
                title: long(255),
                helpText: long(2048),
                requirements: [{...base.sections[0].requirements[0], prompt: long(1024), helpText: long(2048)}],
            }],
        });

        expect(templateDraftProblems(atLimit).map(problem => problem.message).filter(message => message.includes("longer than")))
            .toEqual([]);
    });
});
