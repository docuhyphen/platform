/** @vitest-environment jsdom */
import {act, cleanup, renderHook} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestContributorRole,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {responseKey} from "./structuredResponseWorkspaceState.ts";
import {StructuredResponseWorkspaceProps} from "./StructuredResponseWorkspaceTypes.ts";
import {useStructuredResponseWorkspaceController} from "./useStructuredResponseWorkspaceController.ts";

const onSaveResponses = vi.fn<StructuredResponseWorkspaceProps["onSaveResponses"]>();
const onRefresh = vi.fn();

const STORAGE_KEY = "information-request-unsaved:request-protected:respondent-a";

const requirement = (key: string): InformationRequestTemplateRequirementDto => ({
    id: `binding-${key}`,
    templateRequirementId: `template-${key}`,
    requirementKey: key,
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt: `Provide the ${key}`,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const response = (key: string): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${key}`,
    sourceTemplateRequirementId: `template-${key}`,
    sourceTemplateBindingId: `binding-${key}`,
    occurrencePath: "root",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    narrative: null,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: Date.parse("2026-10-26T00:00:00Z"),
});

const props = (keys: string[]): StructuredResponseWorkspaceProps => ({
    request: {
        id: "request-protected",
        exchangeId: "exchange-1",
        templateVersionId: "template-version-1",
        ownerType: InformationRequestOwnerType.USER,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: false,
        aggregateRevision: 1,
        createdAt: Date.parse("2026-10-26T00:00:00Z"),
        updatedAt: Date.parse("2026-10-26T00:00:00Z"),
        requestETag: "\"request:1\"",
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        conditionEvaluations: [],
    },
    respondentKey: "respondent-a",
    responseETag: "\"responses:1\"",
    groups: [],
    occurrences: [],
    requirements: keys.map(requirement),
    bindings: [],
    responses: keys.map(response),
    onSaveResponses,
    onAddOccurrence: vi.fn(),
    onRemoveOccurrence: vi.fn(),
    onReorderOccurrences: vi.fn(),
    onRefresh,
});

const explain = (key: string, narrative: string) => ({
    [responseKey(`binding-${key}`, "root")]: {
        disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
        narrative
    },
});

const elapse = (milliseconds: number) => act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
});

describe("a protected item whose verification lapsed while it was being answered", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it("reloads to hide the item, keeps its answer in the tab and saves the other answers", async () => {
        onSaveResponses.mockRejectedValueOnce({
            errorMessage: "This protected item needs you to verify again before it can be saved",
            reasonCode: "INFORMATION_REQUEST_CONFIDENTIALITY_DENIED",
        });
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
        const view = renderHook(useStructuredResponseWorkspaceController, {initialProps: props(["record", "restricted"])});
        act(() => view.result.current.setAnswers(() => ({...explain("record", "Not kept"), ...explain("restricted", "Held back")})));
        await elapse(2000);

        expect(onRefresh).toHaveBeenCalledTimes(1);
        expect(view.result.current.error).toBe("A protected question needs you to verify again. Your answer to it stays in this tab until you do.");

        view.rerender(props(["record"]));
        await elapse(2000);

        expect(onSaveResponses).toHaveBeenCalledTimes(2);
        expect(onSaveResponses.mock.calls[1][1].patches).toEqual([{
            requirementId: "runtime-record",
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "Not kept",
        }]);
        expect(window.sessionStorage.getItem(STORAGE_KEY)).toContain("Held back");
    });
});
