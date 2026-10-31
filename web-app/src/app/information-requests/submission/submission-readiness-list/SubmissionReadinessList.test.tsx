/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestSubmissionProblemCode,
    InformationRequestSubmissionProblemDto,
} from "../../../models/models.tsx";
import SubmissionReadinessList from "./SubmissionReadinessList.tsx";

const problems: InformationRequestSubmissionProblemDto[] = [
    {
        requirementId: "requirement-a",
        requirementKey: "supporting-record",
        occurrencePath: "root",
        code: InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE,
    },
    {
        requirementId: "requirement-a",
        requirementKey: "supporting-record",
        occurrencePath: "root",
        code: InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED,
    },
    {
        requirementId: "requirement-b",
        requirementKey: "additional-record",
        occurrencePath: "root",
        code: InformationRequestSubmissionProblemCode.EVIDENCE_NOT_CONFORMING,
    },
];

describe("SubmissionReadinessList", () => {
    afterEach(cleanup);

    it("links a group blocker to its add-entry control", () => {
        render(<>
            <button id={"information-request-add-occurrence-records"}>Add records</button>
            <SubmissionReadinessList ready={false}
                                     problems={[]}
                                     undisclosedProblemCount={0}
                                     groupProblems={[{
                                         groupKey: "records",
                                         minOccurrences: 1,
                                         maxOccurrences: 3,
                                         occurrenceCount: 0
                                     }]}
                                     requirementLabels={{}}/>
        </>);
        const target = screen.getByRole("button", {name: "Add records"});
        target.scrollIntoView = vi.fn();
        fireEvent.click(screen.getByRole("button", {name: "Go to records"}));
        expect(target.scrollIntoView).toHaveBeenCalled();
        expect(document.activeElement).toBe(target);
    });
    it("gives each problem and its explanation an id unique to the requirement and the problem", () => {
        const {container} = render(<SubmissionReadinessList ready={false}
                                                            problems={problems}
                                                            undisclosedProblemCount={0}
                                                            groupProblems={[]}
                                                            requirementLabels={{"requirement-a": "Supporting record"}}/>);
        const text = (id: string) => container.querySelector(`#${id}`)?.textContent;
        const problemIds = Array.from(container.querySelectorAll("#information-request-submission-problems > li"))
            .map(problem => problem.id);

        expect(problemIds).toEqual([
            "information-request-submission-problem-requirement-a-requirement_incomplete",
            "information-request-submission-problem-requirement-a-reconfirmation_required",
            "information-request-submission-problem-requirement-b-evidence_not_conforming",
        ]);
        expect(text("information-request-submission-problem-requirement-a-requirement_incomplete-detail")).toBe("Still needs an answer");
        expect(text("information-request-submission-problem-requirement-a-reconfirmation_required-detail"))
            .toBe("Changed by an amendment; save it again to confirm");
        expect(text("information-request-submission-problem-requirement-b-evidence_not_conforming-detail"))
            .toBe("Its files do not meet the request yet");
        expect(text("information-request-submission-problem-requirement-b-evidence_not_conforming-go")).toBe("additional record");
    });

    it("explains a repeatable group with too few entries, including one nested in another entry", () => {
        const {container} = render(<SubmissionReadinessList ready={false}
                                                            problems={[]}
                                                            undisclosedProblemCount={0}
                                                            groupProblems={[
                                                                {
                                                                    groupKey: "record-entries",
                                                                    parentOccurrencePath: null,
                                                                    minOccurrences: 2,
                                                                    maxOccurrences: 4,
                                                                    occurrenceCount: 1
                                                                },
                                                                {
                                                                    groupKey: "details",
                                                                    parentOccurrencePath: "record-entries[0]",
                                                                    minOccurrences: 1,
                                                                    maxOccurrences: null,
                                                                    occurrenceCount: 0
                                                                },
                                                            ]}
                                                            requirementLabels={{}}/>);
        const text = (id: string) => container.querySelector(`#${id}`)?.textContent;

        expect(text("information-request-submission-group-problem-0-label")).toBe("record entries");
        expect(text("information-request-submission-group-problem-0-detail")).toBe("Needs at least 2 entries; 1 entry added");
        expect(text("information-request-submission-group-problem-1-label")).toBe("details (in record entries)");
        expect(text("information-request-submission-group-problem-1-detail")).toBe("Needs at least 1 entry; 0 entries added");
        expect(container.querySelector("#information-request-submission-ready")).toBeNull();
    });
});
