import {useCallback, useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {getInformationRequestResponseWorkspace} from "../../../../services/informationRequestRuntimeService.ts";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkIssuedDto,
    InformationRequestPartyDto,
    InformationRequestPartyListingDto,
    InformationRequestResponseWorkspaceDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useAuthorCommandRunner} from "./useAuthorCommandRunner.ts";

export interface ShownAccessLink
{
    url: string;
    partyLabel: string;
}

const EMPTY_PARTIES: InformationRequestPartyListingDto = {parties: [], partiesETag: ""};

export const partyLabel = (party: InformationRequestPartyDto): string => party.label ?? "Unnamed party";

export const useAuthorWorkspace = (requestId: string) =>
{
    const {appUser} = useAuth();
    const [workspace, setWorkspace] = useState<InformationRequestResponseWorkspaceDto | null>(null);
    const [parties, setParties] = useState<InformationRequestPartyListingDto>(EMPTY_PARTIES);
    const [links, setLinks] = useState<InformationRequestAccessLinkDto[]>([]);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [shownLink, setShownLink] = useState<ShownAccessLink | null>(null);

    const load = useCallback(async () =>
    {
        try
        {
            const [loadedWorkspace, loadedParties, loadedLinks] = await Promise.all([
                getInformationRequestResponseWorkspace(requestId),
                authoring.getInformationRequestParties(requestId),
                authoring.getInformationRequestAccessLinks(requestId).catch(() => []),
            ]);
            setWorkspace(loadedWorkspace);
            setParties(loadedParties);
            setLinks(loadedLinks);
            setLoadError(null);
        }
        catch (caught: unknown)
        {
            setLoadError(informationRequestRefusalMessage(caught, "This Information Request could not be loaded."));
        }
    }, [requestId]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const runner = useAuthorCommandRunner(load);
    const requestETag = workspace?.request.requestETag ?? "";

    const show = (party: InformationRequestPartyDto, issued: InformationRequestAccessLinkIssuedDto | null) =>
    {
        if (!issued) return;
        setShownLink({
            url: authoring.informationRequestAccessLinkUrl(requestId, issued.accessToken, window.location.origin),
            partyLabel: partyLabel(party),
        });
    };

    const assignParty = (request: AssignInformationRequestPartyRequest) =>
        runner.run(`assign:${JSON.stringify(request)}:${parties.partiesETag}`, key =>
            authoring.assignInformationRequestParty(requestId, request, parties.partiesETag, key), "The party was added.");

    return {
        workspace,
        parties: parties.parties,
        links,
        loadError,
        shownLink,
        busy: runner.busy,
        error: runner.error ?? loadError,
        notice: runner.notice,
        reload: load,
        dismissShownLink: () => setShownLink(null),
        issue: () => runner.run(`issue:${requestETag}`, key =>
            authoring.issueInformationRequest(requestId, requestETag, key), "The request was issued."),
        cancel: (reasonCode: string) => runner.run(`cancel:${requestETag}:${reasonCode}`, key =>
            authoring.cancelInformationRequest(requestId, reasonCode, requestETag, key), "The request was cancelled."),
        supersede: (replacementId: string, reasonCode: string) => runner.run(`supersede:${requestETag}:${replacementId}`, key =>
            authoring.supersedeInformationRequest(requestId, replacementId, reasonCode, requestETag, key), "The request was superseded."),
        assignParty,
        assignSubject: (request: AssignInformationRequestSubjectRequest) =>
            runner.run(`subject:${JSON.stringify(request)}:${parties.partiesETag}`, key =>
                authoring.assignInformationRequestSubject(requestId, request, parties.partiesETag, key), "The subject was named."),
        revokeParty: (party: InformationRequestPartyDto) =>
            runner.run(`revoke:${party.id}:${parties.partiesETag}`, key =>
                authoring.revokeInformationRequestParty(requestId, party.id, parties.partiesETag, key), "The party was removed."),
        nameSelfDecisionMaker: () => appUser?.id
            ? assignParty({roleKey: InformationRequestShareRoleKey.DECISION_MAKER, userId: appUser.id})
            : Promise.resolve(null),
        issueLink: async (party: InformationRequestPartyDto) => show(party, await runner.run(`link:${party.id}:${party.partyETag}`, key =>
            authoring.issueInformationRequestAccessLink(requestId, {partyId: party.id}, party.partyETag, key))),
        resendLink: async (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) =>
            show(party, await runner.run(`rotate:${link.shareLinkId}:${link.rotationCount}`, key =>
                authoring.rotateInformationRequestAccessLink(requestId, link.shareLinkId, party.partyETag, key))),
        revokeLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) =>
            runner.run(`unlink:${link.shareLinkId}`, key =>
                authoring.revokeInformationRequestAccessLink(requestId, link.shareLinkId, party.partyETag, key), "The link was revoked."),
    };
};

export type AuthorWorkspaceState = ReturnType<typeof useAuthorWorkspace>;
