import {useCallback, useEffect, useMemo, useState} from "react";
import {useParams, useSearchParams} from "react-router-dom";
import {
    getInformationRequestAccessToken,
    getInformationRequestSessionToken,
    getInformationRequestResponseWorkspace,
    issueInformationRequestContactProofChallenge,
    storeInformationRequestAccessToken,
    storeInformationRequestSessionToken,
    verifyInformationRequestContactProofChallenge,
} from "../../../services/informationRequestRuntimeService.ts";
import {
    InformationRequestResponseWorkspaceDto,
    InformationRequestTemplateRequirementDto,
    ResponseError,
} from "../../models/models.tsx";

export type InformationRequestRespondentAccessMode = "authenticated" | "no-auth";

interface WorkspaceState
{
    requestId: string;
    workspace: InformationRequestResponseWorkspaceDto | null;
    requirements: InformationRequestTemplateRequirementDto[];
    accessLinkToken?: string;
    accessVerified: boolean;
    challengeSent: boolean;
    code: string;
    busy: boolean;
    error: string | null;
    setCode: (value: string) => void;
    issueChallenge: () => Promise<void>;
    verifyCode: () => Promise<void>;
    loadWorkspace: () => Promise<void>;
}

const errorMessage = (error: unknown): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    const responseError = error as ResponseError;
    return responseError?.errorMessage ?? "The Information Request could not be loaded.";
};

export const useInformationRequestRespondentWorkspace = (
    accessMode: InformationRequestRespondentAccessMode,
): WorkspaceState =>
{
    const params = useParams<{requestId: string}>();
    const [searchParams, setSearchParams] = useSearchParams();
    const requestId = accessMode === "authenticated" ? params.requestId ?? "" : searchParams.get("s") ?? "";
    const [workspace, setWorkspace] = useState<InformationRequestResponseWorkspaceDto | null>(null);
    const [accessVerified, setAccessVerified] = useState(accessMode === "authenticated");
    const [challengeSent, setChallengeSent] = useState(false);
    const [code, setCode] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [storedAccessLinkToken, setStoredAccessLinkToken] = useState("");

    const accessLinkToken = useMemo(() => accessMode === "no-auth" ? storedAccessLinkToken : undefined,
        [accessMode, storedAccessLinkToken]);

    useEffect(() =>
    {
        if (accessMode !== "no-auth" || !requestId) return;
        const token = searchParams.get("t");
        if (token)
        {
            storeInformationRequestAccessToken(requestId, token);
            const nextParams = new URLSearchParams(searchParams);
            nextParams.delete("t");
            setSearchParams(nextParams, {replace: true});
        }
        setStoredAccessLinkToken(getInformationRequestAccessToken(requestId));
        setAccessVerified(Boolean(getInformationRequestSessionToken(requestId)));
    }, [accessMode, requestId, searchParams, setSearchParams]);

    const loadWorkspace = useCallback(async () =>
    {
        if (!requestId || (accessMode === "no-auth" && !accessLinkToken)) return;
        setBusy(true);
        setError(null);
        try
        {
            setWorkspace(await getInformationRequestResponseWorkspace(requestId, accessLinkToken));
        }
        catch (caught: unknown)
        {
            const reasonCode = (caught as ResponseError)?.reasonCode;
            if (accessMode === "no-auth" && reasonCode?.startsWith("INFORMATION_REQUEST_ACCESS_SESSION_"))
            {
                storeInformationRequestSessionToken(requestId, "");
                setAccessVerified(false);
                setWorkspace(null);
            }
            setError(errorMessage(caught));
        }
        finally
        {
            setBusy(false);
        }
    }, [accessLinkToken, accessMode, requestId]);

    useEffect(() =>
    {
        if (accessVerified) void loadWorkspace();
    }, [accessVerified, loadWorkspace]);

    const issueChallenge = async () =>
    {
        if (!accessLinkToken) return;
        setBusy(true);
        setError(null);
        try
        {
            await issueInformationRequestContactProofChallenge(accessLinkToken);
            setChallengeSent(true);
        }
        catch (caught: unknown)
        {
            setError(errorMessage(caught));
        }
        finally
        {
            setBusy(false);
        }
    };

    const verifyCode = async () =>
    {
        if (!accessLinkToken || !requestId) return;
        setBusy(true);
        setError(null);
        try
        {
            const session = await verifyInformationRequestContactProofChallenge(accessLinkToken, code);
            storeInformationRequestSessionToken(requestId, session.sessionToken);
            setAccessVerified(true);
        }
        catch (caught: unknown)
        {
            setError(errorMessage(caught));
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        requestId,
        workspace,
        requirements: workspace?.templateVersion.sections.flatMap(section => section.requirements) ?? [],
        accessLinkToken,
        accessVerified,
        challengeSent,
        code,
        busy,
        error,
        setCode,
        issueChallenge,
        verifyCode,
        loadWorkspace,
    };
};
