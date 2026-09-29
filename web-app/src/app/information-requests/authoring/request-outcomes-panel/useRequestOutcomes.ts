import {useCallback, useEffect, useState} from "react";
import {recordInformationRequestPrivacyRequest} from "../../../../services/informationRequestAdministrationService.ts";
import * as outcomes from "../../../../services/informationRequestOutcomeService.ts";
import {
    InformationRequestAcceptedFactDto,
    InformationRequestBusinessDecisionDto,
    InformationRequestItemCorrectionRequest,
    InformationRequestPrivacyRequestKind,
    InformationRequestSubmissionPackageDto,
    PromoteInformationRequestAcceptedFactRequest,
    RecordInformationRequestBusinessDecisionRequest,
    RevokeInformationRequestAcceptedFactRequest,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useAuthorCommandRunner} from "../author-workspace/useAuthorCommandRunner.ts";

export interface CorrectionBasis
{
    purposeKey: string;
    policyBasisKey: string;
}

export const useRequestOutcomes = (requestId: string) =>
{
    const [packages, setPackages] = useState<InformationRequestSubmissionPackageDto[]>([]);
    const [facts, setFacts] = useState<InformationRequestAcceptedFactDto[] | null>(null);
    const [decisions, setDecisions] = useState<InformationRequestBusinessDecisionDto[]>([]);
    const [loaded, setLoaded] = useState(false);
    const [loadError, setLoadError] = useState<string | null>(null);

    const load = useCallback(async () =>
    {
        try
        {
            const [loadedPackages, loadedFacts, loadedDecisions] = await Promise.all([
                outcomes.getInformationRequestSubmissionPackages(requestId),
                outcomes.getInformationRequestAcceptedFacts(requestId).catch(() => null),
                outcomes.getInformationRequestBusinessDecisions(requestId),
            ]);
            setPackages(loadedPackages);
            setFacts(loadedFacts);
            setDecisions(loadedDecisions);
            setLoadError(null);
        }
        catch (caught: unknown)
        {
            setLoadError(informationRequestRefusalMessage(caught, "The outcomes of this request could not be loaded."));
        }
        finally
        {
            setLoaded(true);
        }
    }, [requestId]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const runner = useAuthorCommandRunner(load);

    return {
        packages,
        facts: facts ?? [],
        decisions,
        canManage: facts !== null,
        loaded,
        busy: runner.busy,
        error: runner.error ?? loadError,
        notice: runner.notice,
        promote: (request: PromoteInformationRequestAcceptedFactRequest) =>
            runner.run(`promote:${JSON.stringify(request)}`, key =>
                outcomes.promoteInformationRequestAcceptedFact(requestId, request, key), "The answer was promoted as an accepted fact."),
        revoke: (fact: InformationRequestAcceptedFactDto, request: RevokeInformationRequestAcceptedFactRequest) =>
            runner.run(`revoke-fact:${fact.id}:${request.reasonCode}`, key =>
                outcomes.revokeInformationRequestAcceptedFact(requestId, fact.id, request, key), "The accepted fact was revoked."),
        record: (request: RecordInformationRequestBusinessDecisionRequest) =>
            runner.run(`decision:${JSON.stringify(request)}`, key =>
                outcomes.recordInformationRequestBusinessDecision(requestId, request, key), "The business decision was recorded."),
        correct: (subjectIdentityRefId: string, correction: InformationRequestItemCorrectionRequest, basis: CorrectionBasis) =>
            runner.run(`correct:${JSON.stringify(correction)}`, () => recordInformationRequestPrivacyRequest({
                subjectIdentityRefId,
                requestKind: InformationRequestPrivacyRequestKind.CORRECTION,
                purposeKey: basis.purposeKey,
                policyBasisKey: basis.policyBasisKey,
                correction,
            }), "The correction was recorded."),
    };
};

export type RequestOutcomes = ReturnType<typeof useRequestOutcomes>;
