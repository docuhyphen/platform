/** @vitest-environment jsdom */
import {cleanup, fireEvent, render} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {InformationRequestEvidenceAttribute, InformationRequestRequirementType} from "../../../models/models.tsx";
import RequirementEvidenceAttributes from "./RequirementEvidenceAttributes.tsx";

const draft = {
    requirementKey: "supporting-record",
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt: "Provide the record",
};

describe("RequirementEvidenceAttributes", () => {
    afterEach(cleanup);

    it("offers file types as media types to choose from instead of free text", () => {
        const onChange = vi.fn();
        render(<RequirementEvidenceAttributes id={"requirement"}
                                              draft={draft}
                                              readOnly={false}
                                              onChange={onChange}/>);

        expect(document.getElementById("requirement-accepted-value-input")).toBeNull();
        fireEvent.change(document.getElementById("requirement-accepted-type-select")!, {target: {value: "application/pdf"}});
        fireEvent.click(document.getElementById("requirement-accepted-add")!);

        expect(onChange).toHaveBeenCalledWith({
            evidencePolicy: expect.objectContaining({
                acceptedValues: [{
                    attribute: InformationRequestEvidenceAttribute.CONTENT_TYPE,
                    acceptedValue: "application/pdf"
                }],
            }),
        });
    });

    it("lets other attributes be typed", () => {
        const onChange = vi.fn();
        render(<RequirementEvidenceAttributes id={"requirement"}
                                              draft={draft}
                                              readOnly={false}
                                              onChange={onChange}/>);

        fireEvent.change(document.getElementById("requirement-accepted-attribute-select")!,
            {target: {value: InformationRequestEvidenceAttribute.LANGUAGE}});
        fireEvent.change(document.getElementById("requirement-accepted-value-input")!, {target: {value: "en"}});
        fireEvent.click(document.getElementById("requirement-accepted-add")!);

        expect(onChange).toHaveBeenCalledWith({
            evidencePolicy: expect.objectContaining({
                acceptedValues: [{attribute: InformationRequestEvidenceAttribute.LANGUAGE, acceptedValue: "en"}],
            }),
        });
    });
});
