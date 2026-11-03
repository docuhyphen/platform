import {useCallback, useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {getInformationRequestManagementWorkspace} from "../../../../services/informationRequestRuntimeService.ts";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkIssuedDto,
    InformationRequestDocumentPlaceholderDto,
    InformationRequestPartyDto,
    InformationRequestPartyListingDto,
    InformationRequestResponseWorkspaceDto,
    InformationRequestShareRoleKey,
    ReassignInformationRequestPartyRequest,
} from "../../../models/models.tsx";
import {
    informationRequestRefusalMessage,
    isInformationRequestForbidden,
    isInformationRequestReadAccessLost
} from "../../shared/informationRequestRefusal.ts";
import {Scoped, useScopeGuard, valueInScope} from "../../shared/useScopeGuard.ts";
import {useAuthorCommandRunner} from "./useAuthorCommandRunner.ts";

export interface ShownAccessLink
{
    url: string;
    partyLabel: string;
}

interface PartRead<T> {
    value: T | null;
    error: string | null;
}

interface LoadedAuthorWorkspace {
    readGeneration: number;
    workspace: InformationRequestResponseWorkspaceDto;
    parties: InformationRequestPartyListingDto;
    links: PartRead<InformationRequestAccessLinkDto[]>;
    placeholders: PartRead<InformationRequestDocumentPlaceholderDto[]>;
    shownLink: ShownAccessLink | null;
}

const partRead = <T, >(read: Promise<T>, failure: string): Promise<PartRead<T>> =>
    read.then(
        (value): PartRead<T> => ({value, error: null}),
        (refusal: unknown): PartRead<T> => ({
            value: null,
            error: isInformationRequestForbidden(refusal) ? null : informationRequestRefusalMessage(refusal, failure),
        }),
    );

export const partyLabel = (party: InformationRequestPartyDto): string => party.label ?? "Unnamed party";

const withShownLink = (loaded: Scoped<LoadedAuthorWorkspace> | null, owner: string, shownLink: ShownAccessLink | null) =>
    loaded?.scope === owner ? {scope: owner, value: {...loaded.value, shownLink}} : loaded;

export const useAuthorWorkspace = (requestId: string) =>
{
    const {appUser} = useAuth();
    const guard = useScopeGuard(requestId);
    const [loaded, setLoaded] = useState<Scoped<LoadedAuthorWorkspace> | null>(null);
    const [failure, setFailure] = useState<Scoped<string> | null>(null);

    const load = useCallback(async () =>
    {
        const current = guard.beginLoad(requestId);
        if (!current) return;
        try
        {
            const [workspace, parties, links, placeholders] = await Promise.all([
                getInformationRequestManagementWorkspace(requestId),
                authoring.getInformationRequestParties(requestId),
                partRead(authoring.getInformationRequestAccessLinks(requestId), "The access links could not be loaded."),
                partRead(authoring.getInformationRequestDocumentPlaceholders(requestId), "The expected documents could not be loaded."),
            ]);
            if (!current()) return;
            setLoaded(previous => ({
                scope: requestId,
                value: {
                    workspace, parties, links, placeholders,
                    readGeneration: (valueInScope(previous, requestId)?.readGeneration ?? 0) + 1,
                    shownLink: valueInScope(previous, requestId)?.shownLink ?? null
                },
            }));
            setFailure(null);
        }
        catch (caught: unknown)
        {
            if (!current()) return;
            if (isInformationRequestReadAccessLost(caught)) setLoaded(null);
            setFailure({
                scope: requestId,
                value: informationRequestRefusalMessage(caught, "This Information Request could not be loaded.")
            });
        }
    }, [guard, requestId]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const runner = useAuthorCommandRunner(requestId, load);
    const scoped = valueInScope(loaded, requestId);
    const current = scoped?.workspace.request.id === requestId ? scoped : null;
    const loadError = valueInScope(failure, requestId);

    const forRequest = <T>(command: (scope: LoadedAuthorWorkspace) => Promise<T | null>): Promise<T | null> =>
        current ? command(current) : Promise.resolve(null);

    const forParty = <T>(party: InformationRequestPartyDto, command: (scope: LoadedAuthorWorkspace) => Promise<T | null>) =>
        forRequest(scope => party.informationRequestId === scope.workspace.request.id ? command(scope) : Promise.resolve(null));

    const forLink = <T>(link: InformationRequestAccessLinkDto, command: (scope: LoadedAuthorWorkspace) => Promise<T | null>) =>
        forRequest(scope => scope.links.value?.some(candidate => candidate.shareLinkId === link.shareLinkId) ? command(scope) : Promise.resolve(null));

    const show = (owner: string, party: InformationRequestPartyDto, issued: InformationRequestAccessLinkIssuedDto | null) =>
    {
        if (!issued || !guard.isCurrent(owner)) return;
        const shown: ShownAccessLink = {
            url: authoring.informationRequestAccessLinkUrl(owner, issued.accessToken, window.location.origin),
            partyLabel: partyLabel(party),
        };
        setLoaded(previous => withShownLink(previous, owner, shown));
    };

    const assignParty = (request: AssignInformationRequestPartyRequest) => forRequest(({workspace, parties}) =>
        runner.run(`assign:${JSON.stringify(request)}:${parties.partiesETag}`, key =>
            authoring.assignInformationRequestParty(workspace.request.id, request, parties.partiesETag, key), "The party was added."));

    return {
        workspace: current?.workspace ?? null,
        readGeneration: current?.readGeneration ?? 0,
        parties: current?.parties.parties ?? [],
        links: current ? current.links.value : [],
        linksError: current?.links.error ?? null,
        placeholders: current?.placeholders.value ?? [],
        placeholdersError: current?.placeholders.error ?? null,
        loadError,
        shownLink: current?.shownLink ?? null,
        busy: runner.busy,
        error: runner.error ?? loadError,
        notice: runner.notice,
        reload: load,
        dismissShownLink: () => setLoaded(previous => withShownLink(previous, requestId, null)),
        issue: () => forRequest(({workspace: {request}}) => runner.run(`issue:${request.requestETag}`, key =>
            authoring.issueInformationRequest(request.id, request.requestETag, key), "The request was issued.")),
        cancel: (reasonCode: string) => forRequest(({workspace: {request}}) => runner.run(`cancel:${request.requestETag}:${reasonCode}`, key =>
            authoring.cancelInformationRequest(request.id, reasonCode, request.requestETag, key), "The request was cancelled.")),
        supersede: (supersededByRequestId: string, reasonCode: string) => forRequest(({workspace: {request}}) =>
            runner.run(`supersede:${JSON.stringify({supersededByRequestId, reasonCode})}:${request.requestETag}`, key =>
                    authoring.supersedeInformationRequest(request.id, supersededByRequestId, reasonCode, request.requestETag, key),
                "The request was superseded.")),
        changeCompletionGate: (gatesExchangeClosure: boolean) => forRequest(({workspace: {request}}) =>
            runner.run(`completion-gate:${request.requestETag}:${gatesExchangeClosure}`, key =>
                    authoring.changeInformationRequestCompletionGate(request.id, gatesExchangeClosure, request.requestETag, key),
                gatesExchangeClosure ? "The Exchange now waits for this request to finish." : "The Exchange no longer waits for this request.")),
        assignParty,
        assignSubject: (request: AssignInformationRequestSubjectRequest) => forRequest(({workspace, parties}) =>
            runner.run(`subject:${JSON.stringify(request)}:${parties.partiesETag}`, key =>
                authoring.assignInformationRequestSubject(workspace.request.id, request, parties.partiesETag, key), "The subject was named.")),
        reassignParty: (party: InformationRequestPartyDto, request: ReassignInformationRequestPartyRequest) => forParty(party, ({
                                                                                                                                    workspace,
                                                                                                                                    parties
                                                                                                                                }) =>
            runner.run(`reassign:${party.id}:${JSON.stringify(request)}:${parties.partiesETag}`, key =>
                    authoring.reassignInformationRequestParty(workspace.request.id, party.id, request, parties.partiesETag, key),
                "The party was reassigned.")),
        revokeParty: (party: InformationRequestPartyDto) => forParty(party, ({workspace, parties}) =>
            runner.run(`revoke:${party.id}:${parties.partiesETag}`, key =>
                authoring.revokeInformationRequestParty(workspace.request.id, party.id, parties.partiesETag, key), "The party was removed.")),
        nameSelfDecisionMaker: () => appUser?.id
            ? assignParty({roleKey: InformationRequestShareRoleKey.DECISION_MAKER, userId: appUser.id})
            : Promise.resolve(null),
        issueLink: (party: InformationRequestPartyDto) => forParty(party, async ({workspace: {request}}): Promise<null> => {
            show(request.id, party, await runner.run(`link:${party.id}:${party.partyETag}`, key =>
                authoring.issueInformationRequestAccessLink(request.id, {partyId: party.id}, party.partyETag, key)));
            return null;
        }),
        resendLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => forLink(link, async ({workspace: {request}}): Promise<null> => {
            show(request.id, party, await runner.run(`rotate:${link.shareLinkId}:${link.rotationCount}`, key =>
                authoring.rotateInformationRequestAccessLink(request.id, link.shareLinkId, party.partyETag, key)));
            return null;
        }),
        revokeLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => forLink(link, ({workspace: {request}}) =>
            runner.run(`unlink:${link.shareLinkId}`, key =>
                authoring.revokeInformationRequestAccessLink(request.id, link.shareLinkId, party.partyETag, key), "The link was revoked.")),
    };
};

export type AuthorWorkspaceState = ReturnType<typeof useAuthorWorkspace>;
