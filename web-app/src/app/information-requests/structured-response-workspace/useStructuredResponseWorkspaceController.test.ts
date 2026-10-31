/** @vitest-environment jsdom */
import {act, cleanup, renderHook, RenderHookResult} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestContributorRole,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
} from "../../models/models.tsx";
import {responseKey} from "./structuredResponseWorkspaceState.ts";
import {SaveResponsesResult, StructuredResponseWorkspaceProps} from "./StructuredResponseWorkspaceTypes.ts";
import {useStructuredResponseWorkspaceController} from "./useStructuredResponseWorkspaceController.ts";

type Controller = ReturnType<typeof useStructuredResponseWorkspaceController>;
type View = RenderHookResult<Controller, StructuredResponseWorkspaceProps>;

const onSaveResponses = vi.fn<StructuredResponseWorkspaceProps["onSaveResponses"]>();
const onRefresh = vi.fn();

const props = (
    revision: number,
    disposition: InformationRequestResponseDisposition,
    narrative: string | null,
): StructuredResponseWorkspaceProps => ({
    request: {
        id: "request-revision",
        exchangeId: "exchange-1",
        templateVersionId: "template-version-1",
        ownerType: InformationRequestOwnerType.USER,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: false,
        aggregateRevision: revision,
        createdAt: Date.parse("2026-10-20T00:00:00Z"),
        updatedAt: Date.parse("2026-10-20T00:00:00Z"),
        requestETag: `"request:${revision}"`,
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        conditionEvaluations: [],
    },
    respondentKey: "respondent-a",
    responseETag: `"responses:${revision}"`,
    groups: [],
    occurrences: [],
    requirements: [{
        id: "template-binding-1",
        templateRequirementId: "template-requirement-1",
        requirementKey: "supporting-record",
        requirementType: InformationRequestRequirementType.DOCUMENT,
        prompt: "Provide the supporting record",
        responseMode: InformationRequestResponseMode.PROVIDE,
        requiredness: InformationRequestRequiredness.REQUIRED,
        contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
        reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
        permittedDispositions: [
            InformationRequestResponseDisposition.PROVIDED,
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
        ],
        substituteRequirementKeys: [],
        supportingEvidenceRequirementKeys: [],
    }],
    bindings: [],
    responses: [{
        informationRequestRequirementId: "runtime-requirement-1",
        sourceTemplateRequirementId: "template-requirement-1",
        sourceTemplateBindingId: "template-binding-1",
        occurrencePath: "root",
        disposition,
        narrative,
        fieldValues: [],
        responseRevision: revision,
        updatedAt: Date.parse("2026-10-20T00:00:00Z"),
    }],
    onSaveResponses,
    onAddOccurrence: vi.fn(),
    onRemoveOccurrence: vi.fn(),
    onReorderOccurrences: vi.fn(),
    onRefresh,
});

const explainNotApplicable = (view: View, narrative: string) => act(() => view.result.current.setAnswers(() => ({
    [responseKey("template-binding-1", "root")]: {
        disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
        narrative,
    },
})));

const elapse = (milliseconds: number) => act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
});

const savedThenChangedElsewhere = async (): Promise<View> => {
    const view = renderHook(useStructuredResponseWorkspaceController, {
        initialProps: props(1, InformationRequestResponseDisposition.NOT_ANSWERED, null),
    });
    explainNotApplicable(view, "First explanation");
    await elapse(2000);
    expect(onSaveResponses).toHaveBeenCalledTimes(1);
    view.rerender(props(2, InformationRequestResponseDisposition.NOT_APPLICABLE, "First explanation"));
    expect(view.result.current.status).toBe("All changes saved.");
    view.rerender(props(3, InformationRequestResponseDisposition.UNAVAILABLE, "Changed on another device"));
    return view;
};

describe("structured response autosave follows the server's latest answers", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it.each(["First explanation", "Second explanation"])(
        "autosaves %s entered after another device changed the saved answer",
        async narrative => {
            const view = await savedThenChangedElsewhere();

            explainNotApplicable(view, narrative);
            expect(view.result.current.status).toBe("Unsaved changes. Saving shortly.");
            await elapse(2000);

            expect(onSaveResponses).toHaveBeenCalledTimes(2);
            expect(onSaveResponses.mock.calls[1][1].patches).toEqual([{
                requirementId: "runtime-requirement-1",
                disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
                narrative,
            }]);
            expect(onSaveResponses.mock.calls[1][2]).toBe("\"responses:3\"");
        },
    );

    it("saves the earlier explanation again on request after another device changed it", async () => {
        const view = await savedThenChangedElsewhere();

        explainNotApplicable(view, "First explanation");
        await act(async () => {
            await view.result.current.save();
        });

        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][2]).toBe("\"responses:3\"");
    });

    it("sends saved answers once while the read that follows the save is pending, also after an entry command", async () => {
        const view = renderHook(useStructuredResponseWorkspaceController, {
            initialProps: props(1, InformationRequestResponseDisposition.NOT_ANSWERED, null),
        });
        explainNotApplicable(view, "First explanation");
        await elapse(2000);
        expect(view.result.current.status).toBe("All changes saved.");

        act(() => view.result.current.applyResult({outcome: "SAVED", responseETag: "\"responses:3\""}));
        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(view.result.current.status).toBe("All changes saved.");

        view.rerender(props(3, InformationRequestResponseDisposition.NOT_APPLICABLE, "First explanation"));
        await elapse(5000);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
        expect(view.result.current.status).toBe("All changes saved.");
    });

    it("saves an edit made while the earlier save was in flight once that save answers", async () => {
        let answer: (result: SaveResponsesResult) => void = () => undefined;
        onSaveResponses.mockImplementationOnce(() => new Promise<SaveResponsesResult>(resolve => {
            answer = resolve;
        }));
        const view = renderHook(useStructuredResponseWorkspaceController, {
            initialProps: props(1, InformationRequestResponseDisposition.NOT_ANSWERED, null),
        });
        explainNotApplicable(view, "First explanation");
        await elapse(2000);
        expect(view.result.current.busy).toBe(true);

        explainNotApplicable(view, "Second explanation");
        await act(async () => answer({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []}));
        expect(view.result.current.status).toBe("Unsaved changes. Saving shortly.");
        await elapse(2000);

        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][1].patches[0].narrative).toBe("Second explanation");
        expect(onSaveResponses.mock.calls[1][2]).toBe("\"responses:2\"");
    });
});
