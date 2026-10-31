import {useEffect, useState} from "react";

export interface AccountLinkAttempt {
    requestId: string;
    sessionId: string;
    sessionCredential: string;
    respondentKey: string;
    account: string;
    idempotencyKey: string;
    partyETag: string;
}

export interface PendingAccountLink {
    attempt: AccountLinkAttempt | null;
    keep: (attempt: AccountLinkAttempt | null) => void;
}

export const usePendingAccountLink = (requestId: string, sessionId: string): PendingAccountLink => {
    const [kept, keep] = useState<AccountLinkAttempt | null>(null);
    const superseded = kept !== null
        && (kept.requestId !== requestId || (sessionId !== "" && kept.sessionId !== sessionId));

    useEffect(() => {
        if (superseded) keep(null);
    }, [superseded]);

    return {attempt: superseded ? null : kept, keep};
};
