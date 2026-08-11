import {
    PlanCode,
    PlanFeature,
    SubscriptionDenialDto,
    SubscriptionDenialReason,
} from "../app/models/models.tsx";

interface ErrorEnvelope
{
    response?: {data?: unknown};
}

const PLAN_CODES = new Set<string>(Object.values(PlanCode));
const PLAN_FEATURES = new Set<string>(Object.values(PlanFeature));
const DENIAL_REASONS = new Set<string>(Object.values(SubscriptionDenialReason));

const isRecord = (value: unknown): value is Record<string, unknown> =>
    typeof value === "object" && value !== null;

const unwrapErrorPayload = (error: unknown): unknown =>
{
    if (!isRecord(error)) return error;
    const response = (error as ErrorEnvelope).response;
    return response?.data ?? error;
};

const optionalNumber = (value: unknown): number | null | undefined =>
    value === null || typeof value === "number" ? value : undefined;

export const parseSubscriptionDenial = (error: unknown): SubscriptionDenialDto | null =>
{
    const payload = unwrapErrorPayload(error);
    if (!isRecord(payload) ||
        typeof payload.errorMessage !== "string" ||
        typeof payload.reasonCode !== "string" ||
        !DENIAL_REASONS.has(payload.reasonCode) ||
        typeof payload.planCode !== "string" ||
        !PLAN_CODES.has(payload.planCode))
    {
        return null;
    }

    const featureCode = payload.featureCode;
    const upgradePlanCode = payload.upgradePlanCode;

    return {
        errorMessage: payload.errorMessage,
        reasonCode: payload.reasonCode as SubscriptionDenialReason,
        planCode: payload.planCode as PlanCode,
        featureCode: typeof featureCode === "string" && PLAN_FEATURES.has(featureCode)
            ? featureCode as PlanFeature
            : null,
        currentValue: optionalNumber(payload.currentValue),
        limit: optionalNumber(payload.limit),
        upgradePlanCode: typeof upgradePlanCode === "string" && PLAN_CODES.has(upgradePlanCode)
            ? upgradePlanCode as PlanCode
            : null,
    };
};

export const getSubscriptionDenialMessage = (error: unknown): string | null =>
    parseSubscriptionDenial(error)?.errorMessage ?? null;
