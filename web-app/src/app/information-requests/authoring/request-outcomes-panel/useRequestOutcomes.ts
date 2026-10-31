import {useCallback, useEffect, useState} from "react";
import {recordInformationRequestPrivacyRequest} from "../../../../services/informationRequestAdministrationService.ts";
import * as outcomes from "../../../../services/informationRequestOutcomeService.ts";
import {listInformationRequestReviews} from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestAcceptedFactDto,
    InformationRequestBusinessDecisionDto,
    InformationRequestItemCorrectionRequest,
    InformationRequestPrivacyRequestKind,
    InformationRequestReviewDto,
    InformationRequestSubmissionPackageDto,
    PromoteInformationRequestAcceptedFactRequest,
    RecordInformationRequestBusinessDecisionRequest,
    RevokeInformationRequestAcceptedFactRequest,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage, unlessForbidden} from "../../shared/informationRequestRefusal.ts";
import {Scoped, useScopeGuard, valueInScope} from "../../shared/useScopeGuard.ts";
import {useAuthorCommandRunner} from "../author-workspace/useAuthorCommandRunner.ts";
import {useRequestReadRefresh} from "../../shared/request-read-refresh/RequestReadRefreshContext.ts";

export interface CorrectionBasis
{
    purposeKey: string;
    policyBasisKey: string;
}

interface LoadedOutcomes {
    packages: InformationRequestSubmissionPackageDto[];
    facts: InformationRequestAcceptedFactDto[] | null;
    decisions: InformationRequestBusinessDecisionDto[] | null;
    reviews: InformationRequestReviewDto[] | null;
}

interface SettledOutcomes {
    outcomes: LoadedOutcomes | null;
    error: string | null;
}

export const useRequestOutcomes = (requestId: string) =>
{
    const guard = useScopeGuard(requestId);
    const refresh = useRequestReadRefresh();
    const [settled, setSettled] = useState<Scoped<SettledOutcomes> | null>(null);

    const load = useCallback(async () =>
    {
        const current = guard.beginLoad(requestId);
        if (!current) return;
        try
        {
            const [packages, facts, decisions, reviews] = await Promise.all([
                outcomes.getInformationRequestSubmissionPackages(requestId),
                unlessForbidden(outcomes.getInformationRequestAcceptedFacts(requestId)),
                unlessForbidden(outcomes.getInformationRequestBusinessDecisions(requestId)),
                unlessForbidden(listInformationRequestReviews(requestId)),
            ]);
            if (current()) setSettled({
                scope: requestId,
                value: {outcomes: {packages, facts, decisions, reviews}, error: null}
            });
        }
        catch (caught: unknown)
        {
            if (current()) setSettled({
                scope: requestId,
                value: {
                    outcomes: null,
                    error: informationRequestRefusalMessage(caught, "The outcomes of this request could not be loaded.")
                },
            });
        }
    }, [guard, requestId]);

    useEffect(() =>
    {
        void load();
    }, [load, refresh]);

    const runner = useAuthorCommandRunner(requestId, load);
    const shown = valueInScope(settled, requestId);
    const facts = shown?.outcomes?.facts ?? null;
    const decisions = shown?.outcomes?.decisions ?? null;

    return {
        packages: shown?.outcomes?.packages ?? [],
        facts: facts ?? [],
        decisions: decisions ?? [],
        reviews: shown?.outcomes?.reviews ?? null,
        decisionsVisible: decisions !== null,
        canManage: facts !== null,
        loaded: shown !== null,
        busy: runner.busy,
        error: runner.error,
        loadError: shown?.error ?? null,
        reload: load,
        notice: runner.notice,
        promote: (request: PromoteInformationRequestAcceptedFactRequest) =>
            runner.run(`promote:${JSON.stringify(request)}`, key =>
                outcomes.promoteInformationRequestAcceptedFact(requestId, request, key), "The answer was promoted as an accepted fact."),
        revoke: (fact: InformationRequestAcceptedFactDto, request: RevokeInformationRequestAcceptedFactRequest) =>
            runner.run(`revoke-fact:${fact.id}:${JSON.stringify(request)}`, key =>
                outcomes.revokeInformationRequestAcceptedFact(requestId, fact.id, request, key), "The accepted fact was revoked."),
        record: (request: RecordInformationRequestBusinessDecisionRequest) =>
            runner.run(`decision:${JSON.stringify(request)}`, key =>
                outcomes.recordInformationRequestBusinessDecision(requestId, request, key), "The business decision was recorded."),
        correct: (subjectIdentityRefId: string, correction: InformationRequestItemCorrectionRequest, basis: CorrectionBasis) =>
            runner.run(`correct:${JSON.stringify({
                subjectIdentityRefId,
                purposeKey: basis.purposeKey,
                policyBasisKey: basis.policyBasisKey,
                correction
            })}`, key => recordInformationRequestPrivacyRequest({
                subjectIdentityRefId,
                requestKind: InformationRequestPrivacyRequestKind.CORRECTION,
                purposeKey: basis.purposeKey,
                policyBasisKey: basis.policyBasisKey,
                correction,
            }, key), "The correction was recorded."),
    };
};

export type RequestOutcomes = ReturnType<typeof useRequestOutcomes>;
