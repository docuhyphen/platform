import {useCallback, useEffect, useRef, useState} from "react";
import type {SetURLSearchParams} from "react-router-dom";
import {
    getInformationRequestAccessToken,
    getInformationRequestSessionId,
    getInformationRequestSessionToken,
    storeInformationRequestAccessToken,
} from "../../../services/informationRequestRuntimeService.ts";
import {useStillMounted} from "../shared/useStillMounted.ts";

export interface RespondentAccessLink {
    requestId: string;
    accessLinkToken: string;
    sessionId: string;
    verified: boolean;
    generation: number;
}

type StoredAccessLink = Omit<RespondentAccessLink, "generation">;

type RespondentSessionChange = Pick<RespondentAccessLink, "sessionId" | "verified">;

export interface RespondentAccessLinks {
    current: RespondentAccessLink | null;
    replace: (owner: RespondentAccessLink, change: RespondentSessionChange, write: () => void) => boolean;
}

const storedAccessLink = (requestId: string): StoredAccessLink => ({
    requestId,
    accessLinkToken: getInformationRequestAccessToken(requestId),
    sessionId: getInformationRequestSessionId(requestId),
    verified: Boolean(getInformationRequestSessionToken(requestId)),
});

const sameAccess = (held: RespondentAccessLink | undefined, stored: StoredAccessLink): boolean =>
    held?.accessLinkToken === stored.accessLinkToken &&
    held.sessionId === stored.sessionId &&
    held.verified === stored.verified;

export const useRespondentAccessLink = (
    enabled: boolean,
    requestId: string,
    linkToken: string | null,
    searchParams: URLSearchParams,
    setSearchParams: SetURLSearchParams,
): RespondentAccessLinks => {
    const [shown, setShown] = useState<RespondentAccessLink | null>(null);
    const held = useRef(new Map<string, RespondentAccessLink>());
    const generations = useRef(0);
    const stillMounted = useStillMounted();

    useEffect(() => {
        if (!enabled || !requestId) return;
        if (linkToken) {
            storeInformationRequestAccessToken(requestId, linkToken);
            const nextParams = new URLSearchParams(searchParams);
            nextParams.delete("t");
            setSearchParams(nextParams, {replace: true});
        }
        const stored = storedAccessLink(requestId);
        const kept = held.current.get(requestId);
        const next = kept && sameAccess(kept, stored) ? kept : {...stored, generation: ++generations.current};
        held.current.set(requestId, next);
        setShown(next);
    }, [enabled, linkToken, requestId, searchParams, setSearchParams]);

    const replace = useCallback((owner: RespondentAccessLink, change: RespondentSessionChange, write: () => void) => {
        if (!stillMounted() || held.current.get(owner.requestId)?.generation !== owner.generation) return false;
        write();
        const next = {...owner, ...change, generation: ++generations.current};
        held.current.set(owner.requestId, next);
        setShown(current => current?.requestId === owner.requestId ? next : current);
        return true;
    }, [stillMounted]);

    const current = shown?.requestId === requestId && (!linkToken || linkToken === shown.accessLinkToken)
        ? shown
        : null;
    return {current, replace};
};
