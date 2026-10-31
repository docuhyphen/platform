import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useParams, useSearchParams} from "react-router-dom";
import {
    clearInformationRequestSession,
    getInformationRequestResponseWorkspace,
    issueInformationRequestContactProofChallenge,
    storeInformationRequestSession,
    verifyInformationRequestContactProofChallenge,
} from "../../../services/informationRequestRuntimeService.ts";
import {
    InformationRequestResponseWorkspaceDto,
    InformationRequestTemplateRequirementDto,
    ResponseError,
} from "../../models/models.tsx";
import {KeptEvidenceUploads, keptEvidenceUploads, KeptUpload} from "../requirement-evidence/keptEvidenceUploads.ts";
import {isInformationRequestAccessSessionEnded} from "../shared/informationRequestRefusal.ts";
import {Scoped, useScopeGuard} from "../shared/useScopeGuard.ts";
import {commandKeysFor} from "../shared/useStableIdempotencyKey.ts";
import {respondentAccessRefusalMessage} from "./respondentAccessRefusal.ts";
import {useRespondentAccessLink} from "./useRespondentAccessLink.ts";

export type InformationRequestRespondentAccessMode = "authenticated" | "no-auth";

interface WorkspaceState
{
    requestId: string;
    workspace: InformationRequestResponseWorkspaceDto | null;
    requirements: InformationRequestTemplateRequirementDto[];
    accessLinkToken?: string;
    sessionId: string;
    accessVerified: boolean;
    linkIncomplete: boolean;
    challengeSent: boolean;
    code: string;
    busy: boolean;
    error: string | null;
    keptUploads: KeptEvidenceUploads;
    commandKeys: Map<string, string>;
    setCode: (value: string) => void;
    issueChallenge: () => Promise<void>;
    verifyCode: () => Promise<void>;
    loadWorkspace: () => Promise<void>;
    endAccessSession: (refusal: unknown) => void;
}

const errorMessage = (error: unknown): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    const responseError = error as ResponseError;
    return respondentAccessRefusalMessage(error)
        ?? responseError?.errorMessage
        ?? "The Information Request could not be loaded.";
};

export const useInformationRequestRespondentWorkspace = (
    accessMode: InformationRequestRespondentAccessMode,
): WorkspaceState =>
{
    const params = useParams<{requestId: string}>();
    const [searchParams, setSearchParams] = useSearchParams();
    const requestId = accessMode === "authenticated" ? params.requestId ?? "" : searchParams.get("s") ?? "";
    const linkToken = accessMode === "no-auth" ? searchParams.get("t") : null;
    const [loaded, setLoaded] = useState<Scoped<InformationRequestResponseWorkspaceDto> | null>(null);
    const [failures, setFailures] = useState<Record<string, string>>({});
    const [entered, setEntered] = useState<Scoped<string> | null>(null);
    const [sentScope, setSentScope] = useState("");
    const [busyScope, setBusyScope] = useState("");
    const [keptStore] = useState(() => new Map<string, KeptUpload[]>());
    const [commandKeyStore] = useState(() => new Map<string, Map<string, string>>());
    const latestLoad = useRef<Scoped<number>>({scope: "", value: 0});

    const {current: currentLink, replace: replaceLink} =
        useRespondentAccessLink(accessMode === "no-auth", requestId, linkToken, searchParams, setSearchParams);
    const accessLinkToken = accessMode === "no-auth" ? currentLink?.accessLinkToken ?? "" : undefined;
    const accessVerified = accessMode === "authenticated" || Boolean(currentLink?.verified);
    const scope = `${accessMode}|${requestId}|${accessLinkToken ?? ""}`;
    const guard = useScopeGuard(scope);
    const workspace = accessVerified && loaded?.scope === scope && loaded.value.request.id === requestId
        ? loaded.value
        : null;
    const respondentKey = workspace?.respondentKey ?? "";
    const keptUploads = useMemo(
        () => keptEvidenceUploads(keptStore, `${scope}|${respondentKey}`),
        [keptStore, respondentKey, scope],
    );
    const commandKeys = useMemo(
        () => commandKeysFor(commandKeyStore, `${requestId}|${respondentKey}`),
        [commandKeyStore, requestId, respondentKey],
    );

    const fail = useCallback((failedScope: string, refusal: unknown) =>
        setFailures(current => ({...current, [failedScope]: errorMessage(refusal)})), []);

    const clearFailure = useCallback((clearedScope: string) =>
        setFailures(current => Object.fromEntries(Object.entries(current).filter(([key]) => key !== clearedScope))), []);

    const endAccessSession = useCallback((refusal: unknown) =>
    {
        if (accessMode !== "no-auth" || !currentLink) return;
        const ended = replaceLink(
            currentLink,
            {sessionId: "", verified: false},
            () => clearInformationRequestSession(currentLink.requestId),
        );
        if (!ended) return;
        setSentScope(current => current === scope ? "" : current);
        setEntered(current => current?.scope === scope ? null : current);
        if (latestLoad.current.scope === scope) latestLoad.current = {scope, value: latestLoad.current.value + 1};
        setLoaded(current => current?.scope === scope ? null : current);
        setBusyScope(current => current === scope ? "" : current);
        fail(scope, refusal);
    }, [accessMode, currentLink, fail, replaceLink, scope]);

    const loadWorkspace = useCallback(async () =>
    {
        if (!requestId || (accessMode === "no-auth" && !accessLinkToken) || !guard.isCurrent(scope)) return;
        const load = latestLoad.current.value + 1;
        latestLoad.current = {scope, value: load};
        const current = () => latestLoad.current.value === load;
        setBusyScope(scope);
        clearFailure(scope);
        try
        {
            const next = await getInformationRequestResponseWorkspace(requestId, accessLinkToken);
            if (current()) setLoaded({scope, value: next});
        }
        catch (caught: unknown)
        {
            if (!current()) return;
            if (accessMode === "no-auth" && isInformationRequestAccessSessionEnded(caught))
            {
                endAccessSession(caught);
                return;
            }
            setLoaded(shown => shown?.scope === scope ? null : shown);
            fail(scope, caught);
        }
        finally
        {
            if (current()) setBusyScope(busy => busy === scope ? "" : busy);
        }
    }, [accessLinkToken, accessMode, clearFailure, endAccessSession, fail, guard, requestId, scope]);

    useEffect(() =>
    {
        if (accessVerified) void loadWorkspace();
    }, [accessVerified, loadWorkspace]);

    const issueChallenge = async () =>
    {
        if (!accessLinkToken) return;
        const issuing = scope;
        setBusyScope(issuing);
        clearFailure(issuing);
        try
        {
            await issueInformationRequestContactProofChallenge(accessLinkToken);
            setSentScope(issuing);
        }
        catch (caught: unknown)
        {
            fail(issuing, caught);
        }
        finally
        {
            setBusyScope(current => current === issuing ? "" : current);
        }
    };

    const code = entered?.scope === scope ? entered.value : "";

    const verifyCode = async () =>
    {
        const owner = currentLink;
        if (!owner || !accessLinkToken || !code) return;
        const verifying = scope;
        setBusyScope(verifying);
        clearFailure(verifying);
        try
        {
            const session = await verifyInformationRequestContactProofChallenge(accessLinkToken, code);
            replaceLink(
                owner,
                {sessionId: session.sessionId, verified: true},
                () => storeInformationRequestSession(owner.requestId, session),
            );
            setSentScope(current => current === verifying ? "" : current);
            setEntered(current => current?.scope === verifying ? null : current);
        }
        catch (caught: unknown)
        {
            fail(verifying, caught);
        }
        finally
        {
            setBusyScope(current => current === verifying ? "" : current);
        }
    };

    return {
        requestId,
        workspace,
        requirements: workspace?.templateVersion.sections.flatMap(section => section.requirements) ?? [],
        accessLinkToken,
        sessionId: currentLink?.sessionId ?? "",
        accessVerified,
        linkIncomplete: accessMode === "no-auth" && Boolean(requestId) && currentLink !== null && !currentLink.accessLinkToken,
        challengeSent: sentScope === scope,
        code,
        busy: busyScope === scope,
        error: failures[scope] ?? null,
        keptUploads,
        commandKeys,
        setCode: value => setEntered({scope, value}),
        issueChallenge,
        verifyCode,
        loadWorkspace,
        endAccessSession,
    };
};
