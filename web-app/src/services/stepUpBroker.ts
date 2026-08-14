// Lightweight broker between the axios response interceptor and the React-rendered
// StepUpModal. The interceptor cannot render UI, so when it detects a
// STEP_UP_REQUIRED response it asks this broker for a password via a Promise.
// The modal subscribes to the broker, prompts the user, and resolves/rejects.
//
// This decouples auth UX from any specific page or feature: every protected admin
// call (Add User, Delete Group, IdP secret rotation, etc.) goes through apiClient
// and will trigger the same step-up flow automatically.

import {
    completeStepUpWithOtp,
    initiateStepUp,
    regenerateStepUpOtp,
    StepUpInitiateResponse,
    StepUpResult,
} from "./authApi";

export type StepUpMethod = 'INTERNAL_EMAIL_OTP' | 'EXTERNAL_RELOGIN';
export type StepUpMfaType = 'EMAIL' | 'GOOGLE_AUTHENTICATOR' | 'MICROSOFT_AUTHENTICATOR';

export interface StepUpPrompt
{
    method: StepUpMethod;
    action?: string | null;
    message?: string | null;
    provider?: string | null;
    authorizeUrl?: string | null;
    mfaSessionId?: string | null;
    mfaType?: StepUpMfaType;
    emailFallbackEnabled?: boolean;
    submitOtp?: (otp: string) => Promise<boolean>;
    resendOtp?: () => Promise<StepUpResult>;
    continueExternal?: () => Promise<void>;
    cancel: () => void;
}

type Subscriber = (prompt: StepUpPrompt | null) => void;

const subscribers = new Set<Subscriber>();
let currentResolver: ((ok: boolean) => void) | null = null;
let pendingPrompt: StepUpPrompt | null = null;

const notify = () =>
{
    subscribers.forEach((s) => s(pendingPrompt));
};

export const subscribeStepUp = (cb: Subscriber): (() => void) =>
{
    subscribers.add(cb);
    cb(pendingPrompt);
    return () => { subscribers.delete(cb); };
};

const resolveAndClose = (ok: boolean) =>
{
    const resolver = currentResolver;
    currentResolver = null;
    pendingPrompt = null;
    notify();
    resolver?.(ok);
};

export const requestStepUp = (opts: {
    action?: string | null;
    message?: string | null;
    returnTo?: string;
}): Promise<boolean> =>
{
    return new Promise<boolean>((resolve, reject) =>
    {
        currentResolver = resolve;

        void (async () =>
        {
            try
            {
                const returnTo = opts.returnTo ?? `${window.location.pathname}${window.location.search}`;
                const initiation: StepUpInitiateResponse = await initiateStepUp(returnTo, opts.action);

                if (initiation.method === 'INTERNAL_EMAIL_OTP')
                {
                    const mfaSessionId = initiation.mfaSessionId;
                    if (!mfaSessionId)
                    {
                        throw new Error('Step-up session is missing');
                    }

                    pendingPrompt = {
                        method: 'INTERNAL_EMAIL_OTP',
                        action: opts.action ?? null,
                        message: initiation.message ?? opts.message ?? null,
                        mfaSessionId,
                        mfaType: initiation.mfaType,
                        emailFallbackEnabled: initiation.emailFallbackEnabled,
                        submitOtp: async (otp: string): Promise<boolean> =>
                        {
                            const result = await completeStepUpWithOtp(mfaSessionId, otp);
                            if (result?.fresh)
                            {
                                resolveAndClose(true);
                                return true;
                            }
                            return false;
                        },
                        resendOtp: initiation.mfaType === 'EMAIL' || initiation.emailFallbackEnabled
                            ? async (): Promise<StepUpResult> =>
                            {
                                return regenerateStepUpOtp(mfaSessionId);
                            }
                            : undefined,
                        cancel: () =>
                        {
                            currentResolver = null;
                            pendingPrompt = null;
                            notify();
                            reject(new Error('Step-up cancelled by user'));
                        },
                    };
                    notify();
                    return;
                }

                const authorizeUrl = initiation.authorizeUrl;
                if (!authorizeUrl)
                {
                    throw new Error('Step-up re-login URL is missing');
                }

                pendingPrompt = {
                    method: 'EXTERNAL_RELOGIN',
                    action: opts.action ?? null,
                    message: initiation.message ?? opts.message ?? null,
                    provider: initiation.provider ?? null,
                    authorizeUrl,
                    continueExternal: async (): Promise<void> =>
                    {
                        // Full-page redirect is intentional: OAuth provider auth must happen top-level.
                        window.location.assign(authorizeUrl);
                    },
                    cancel: () =>
                    {
                        currentResolver = null;
                        pendingPrompt = null;
                        notify();
                        reject(new Error('Step-up cancelled by user'));
                    },
                };
                notify();
            }
            catch (e)
            {
                currentResolver = null;
                pendingPrompt = null;
                notify();
                reject(e);
            }
        })();
    });
};
