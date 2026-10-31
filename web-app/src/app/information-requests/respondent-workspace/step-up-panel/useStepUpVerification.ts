import {useState} from "react";
import {
    issueInformationRequestStepUpChallenge,
    verifyInformationRequestStepUp,
} from "../../../../services/informationRequestRuntimeService.ts";
import {requestStepUp} from "../../../../services/stepUpBroker.ts";
import {ResponseError} from "../../../models/models.tsx";
import {isInformationRequestAccessSessionEnded} from "../../shared/informationRequestRefusal.ts";

const ACCOUNT_VERIFICATION_UNFINISHED = "Verification did not finish.";

interface StepUpVerification {
    code: string;
    codeSent: boolean;
    busy: boolean;
    error: string | null;
    verifiedUntil: number | null;
    setCode: (value: string) => void;
    sendCode: () => Promise<void>;
    verifyCode: () => Promise<void>;
    verifyAccount: () => Promise<void>;
}

const refusalMessage = (error: unknown): string => {
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    return (error as ResponseError)?.errorMessage ?? ACCOUNT_VERIFICATION_UNFINISHED;
};

export const useStepUpVerification = (
    requestId: string,
    accessLinkToken: string | undefined,
    onVerified: () => void,
    onAccessSessionEnded?: (refusal: unknown) => void,
): StepUpVerification => {
    const [code, setCode] = useState("");
    const [codeSent, setCodeSent] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [verifiedUntil, setVerifiedUntil] = useState<number | null>(null);

    const run = async (action: () => Promise<void>) => {
        setBusy(true);
        setError(null);
        try {
            await action();
        } catch (caught: unknown) {
            setError(refusalMessage(caught));
            if (isInformationRequestAccessSessionEnded(caught)) onAccessSessionEnded?.(caught);
        } finally {
            setBusy(false);
        }
    };

    const sendCode = () => run(async () => {
        if (!accessLinkToken) return;
        await issueInformationRequestStepUpChallenge(requestId, accessLinkToken);
        setCodeSent(true);
    });

    const verifyCode = () => run(async () => {
        if (!accessLinkToken) return;
        const stepUp = await verifyInformationRequestStepUp(requestId, accessLinkToken, code);
        setVerifiedUntil(stepUp.verifiedUntil);
        setCode("");
        setCodeSent(false);
        onVerified();
    });

    const verifyAccount = () => run(async () => {
        const verified = await requestStepUp({action: "see protected questions"}).catch(() => false);
        if (!verified) throw new Error(ACCOUNT_VERIFICATION_UNFINISHED);
        onVerified();
    });

    return {code, codeSent, busy, error, verifiedUntil, setCode, sendCode, verifyCode, verifyAccount};
};
