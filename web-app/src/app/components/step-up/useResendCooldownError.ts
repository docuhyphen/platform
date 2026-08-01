import {useCallback, useEffect, useRef, useState} from "react";
import {getOtpFriendlyMessage, NormalizedApiError} from "../../../utils/apiErrorUtils.ts";

export const useResendCooldownError = () =>
{
    const timerRef = useRef<number | null>(null);
    const [cooldownError, setCooldownError] = useState<NormalizedApiError | null>(null);
    const [cooldownRemaining, setCooldownRemaining] = useState(0);

    const clearResendCooldown = useCallback(() =>
    {
        if (timerRef.current !== null)
        {
            window.clearInterval(timerRef.current);
            timerRef.current = null;
        }
        setCooldownError(null);
        setCooldownRemaining(0);
    }, []);

    const startResendCooldown = useCallback((error: NormalizedApiError) =>
    {
        clearResendCooldown();
        const retryAfterSeconds = Math.max(1, Math.ceil(error.retryAfterSeconds ?? 30));
        setCooldownError(error);
        setCooldownRemaining(retryAfterSeconds);
        timerRef.current = window.setInterval(() =>
        {
            setCooldownRemaining((remaining) => Math.max(0, remaining - 1));
        }, 1000);
    }, [clearResendCooldown]);

    useEffect(() =>
    {
        if (cooldownRemaining !== 0)
        {
            return;
        }
        if (timerRef.current !== null)
        {
            window.clearInterval(timerRef.current);
            timerRef.current = null;
        }
        setCooldownError(null);
    }, [cooldownRemaining]);

    useEffect(() => () =>
    {
        if (timerRef.current !== null)
        {
            window.clearInterval(timerRef.current);
        }
    }, []);

    return {
        cooldownMessage: cooldownError && cooldownRemaining > 0
            ? getOtpFriendlyMessage({...cooldownError, retryAfterSeconds: cooldownRemaining})
            : null,
        cooldownRemaining,
        startResendCooldown,
        clearResendCooldown,
    };
};
