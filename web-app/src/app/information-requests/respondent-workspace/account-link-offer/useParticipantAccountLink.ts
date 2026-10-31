import {useState} from "react";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {InformationRequestParticipantAccountUpgradeDto} from "../../../models/models.tsx";
import {
    clearInformationRequestAccessCredentials,
    getInformationRequestNoAuthParties,
    getInformationRequestSessionToken,
    linkInformationRequestParticipantAccount,
} from "../../../../services/informationRequestRuntimeService.ts";
import {
    isInformationRequestAccessSessionEnded,
    isStaleInformationRequestCommand,
} from "../../shared/informationRequestRefusal.ts";
import {informationRequestRespondPath} from "../../shared/informationRequestWorkspacePaths.ts";
import {useStillMounted} from "../../shared/useStillMounted.ts";
import {moveUnsavedResponses} from "../../structured-response-workspace/useUnsavedResponses.ts";
import {accountLinkRefusalMessage, NO_LINKABLE_PARTY} from "./accountLinkRefusal.ts";
import {AccountLinkAttempt, PendingAccountLink} from "./usePendingAccountLink.ts";

const NOT_SIGNED_IN = "You are not signed in yet. Sign in in the other tab, then select I have signed in.";

interface LinkedAccount {
    attempt: AccountLinkAttempt;
    linked: InformationRequestParticipantAccountUpgradeDto;
}

export const useParticipantAccountLink = (
    requestId: string,
    accessLinkToken: string,
    sessionId: string,
    respondentKey: string,
    pending: PendingAccountLink,
    onAccessSessionEnded?: (refusal: unknown) => void,
) => {
    const {appUser, refreshTokens} = useAuth();
    const navigate = useNavigate();
    const stillMounted = useStillMounted();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const account = appUser?.email ?? "";
    const attempt = appUser && pending.attempt?.account === account ? pending.attempt : null;
    const recovering = !sessionId;

    const send = async (next: AccountLinkAttempt): Promise<LinkedAccount> => {
        pending.keep(next);
        const linked = await linkInformationRequestParticipantAccount(
            next.requestId,
            {sessionId: next.sessionId},
            next.partyETag,
            next.idempotencyKey,
            next.sessionCredential,
        );
        return {attempt: next, linked};
    };

    const resend = async (kept: AccountLinkAttempt): Promise<LinkedAccount | null> => {
        try {
            return await send(kept);
        } catch (caught: unknown) {
            if (recovering || !isStaleInformationRequestCommand(caught)) throw caught;
            return null;
        }
    };

    const linkAnew = async (idempotencyKey: string): Promise<LinkedAccount> => {
        const sessionCredential = getInformationRequestSessionToken(requestId);
        const parties = await getInformationRequestNoAuthParties(requestId, accessLinkToken);
        const own = parties.filter(party => party.active && party.principalKind === "PARTICIPANT");
        for (const [index, party] of own.entries()) {
            try {
                return await send({
                    requestId,
                    sessionId,
                    sessionCredential,
                    respondentKey,
                    account,
                    idempotencyKey,
                    partyETag: party.partyETag,
                });
            } catch (caught: unknown) {
                if (!isStaleInformationRequestCommand(caught) || index === own.length - 1) throw caught;
            }
        }
        throw {errorMessage: NO_LINKABLE_PARTY};
    };

    const run = async (action: () => Promise<void>, refusal: (caught: unknown) => string) => {
        if (busy) return;
        setBusy(true);
        setError(null);
        try {
            await action();
        } catch (caught: unknown) {
            setError(refusal(caught));
            if (isInformationRequestAccessSessionEnded(caught)) onAccessSessionEnded?.(caught);
        } finally {
            setBusy(false);
        }
    };

    const link = () => run(async () => {
        if (!appUser || (recovering && !attempt)) return;
        let made: LinkedAccount;
        try {
            made = (attempt && await resend(attempt)) || await linkAnew(attempt?.idempotencyKey ?? crypto.randomUUID());
        } catch (caught: unknown) {
            if (isInformationRequestAccessSessionEnded(caught)) pending.keep(null);
            throw caught;
        }
        if (!stillMounted()) return;
        pending.keep(null);
        moveUnsavedResponses(requestId, made.attempt.respondentKey, made.linked.respondentKey);
        clearInformationRequestAccessCredentials(requestId);
        navigate(informationRequestRespondPath(requestId), {replace: true});
    }, accountLinkRefusalMessage);

    const checkSignIn = () => run(refreshTokens, () => NOT_SIGNED_IN);

    return {
        signedInEmail: appUser ? appUser.email : null,
        recovering,
        offered: !recovering || !appUser || attempt !== null,
        busy,
        error,
        link,
        checkSignIn,
    };
};
