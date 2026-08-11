import {Caption1, ProgressBar} from "@fluentui/react-components";
import {
    PlanCode,
    SubscriptionOwnerType,
} from "../../../models/models.tsx";
import {useCurrentSubscription} from "../../../../hooks/subscription/useCurrentSubscription.ts";
import BillingPlanHeader from "./BillingPlanHeader.tsx";
import {useBillingPlanSummaryStyles} from "./BillingPlanSummaryStyles.tsx";

const formatValue = (value?: string | null): string =>
{
    if (!value) return "Not set";
    return value
        .toLowerCase()
        .split("_")
        .map(part => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");
};

const formatDate = (value?: string | null): string =>
{
    if (!value) return "Not set";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Not set";
    return new Intl.DateTimeFormat(undefined, {dateStyle: "medium"}).format(date);
};

const cappedProgress = (current?: number | null, limit?: number | null): number =>
    limit && limit > 0 ? Math.min((current ?? 0) / limit, 1) : 0;

const BillingPlanSummary = () =>
{
    const styles = useBillingPlanSummaryStyles();
    const subscription = useCurrentSubscription();
    const limits = subscription?.limits ?? null;
    const usage = subscription?.usage ?? null;
    if (!subscription || !limits || !usage) return null;

    const showFreeUsage = subscription.planCode === PlanCode.FREE;
    const showSeatUsage = subscription.ownerType === SubscriptionOwnerType.ORGANIZATION;

    return (
        <section
            id={"settings-billing-current-plan"}
            className={styles.planCard}
            aria-label={"Current plan"}
        >
            <BillingPlanHeader
                planName={formatValue(subscription.planCode)}
                status={formatValue(subscription.status)}
                planHeaderClassName={styles.planHeader}
                planTitleClassName={styles.planTitle}
            />

            <div
                id={"settings-billing-current-plan-facts"}
                className={styles.planFacts}
            >
                <Caption1 id={"settings-billing-current-plan-owner"}>
                    Owner: {formatValue(subscription.ownerType)}
                </Caption1>
                <Caption1 id={"settings-billing-current-plan-frequency"}>
                    Billing frequency: {formatValue(subscription.billingFrequency)}
                </Caption1>
                <Caption1 id={"settings-billing-current-plan-period"}>
                    Current period ends: {formatDate(subscription.currentPeriodEnd)}
                </Caption1>
            </div>

            {showFreeUsage && limits.maxNewExchangesPerCalendarMonth != null && (
                <div
                    id={"settings-billing-monthly-exchange-usage"}
                    className={styles.usageRow}
                >
                    <div
                        id={"settings-billing-monthly-exchange-labels"}
                        className={styles.usageLabels}
                    >
                        <Caption1 id={"settings-billing-monthly-exchange-label"}>Exchanges this month</Caption1>
                        <Caption1 id={"settings-billing-monthly-exchange-value"}>
                            {usage.newExchangesThisPeriod ?? 0} / {limits.maxNewExchangesPerCalendarMonth}
                        </Caption1>
                    </div>
                    <ProgressBar
                        id={"settings-billing-monthly-exchange-progress"}
                        value={cappedProgress(
                            usage.newExchangesThisPeriod,
                            limits.maxNewExchangesPerCalendarMonth,
                        )}
                    />
                </div>
            )}

            {showFreeUsage && limits.maxOpenExchanges != null && (
                <div
                    id={"settings-billing-open-exchange-usage"}
                    className={styles.usageRow}
                >
                    <div
                        id={"settings-billing-open-exchange-labels"}
                        className={styles.usageLabels}
                    >
                        <Caption1 id={"settings-billing-open-exchange-label"}>Open Exchanges</Caption1>
                        <Caption1 id={"settings-billing-open-exchange-value"}>
                            {usage.openExchanges ?? 0} / {limits.maxOpenExchanges}
                        </Caption1>
                    </div>
                    <ProgressBar
                        id={"settings-billing-open-exchange-progress"}
                        value={cappedProgress(usage.openExchanges, limits.maxOpenExchanges)}
                    />
                </div>
            )}

            {showSeatUsage && (
                <div
                    id={"settings-billing-seat-usage"}
                    className={styles.usageRow}
                >
                    <div
                        id={"settings-billing-seat-labels"}
                        className={styles.usageLabels}
                    >
                        <Caption1 id={"settings-billing-seat-label"}>Active seats</Caption1>
                        <Caption1 id={"settings-billing-seat-value"}>
                            {usage.activeSeats ?? 0} / {limits.seatCapacity ?? "Uncapped"}
                        </Caption1>
                    </div>
                    {limits.seatCapacity != null && (
                        <ProgressBar
                            id={"settings-billing-seat-progress"}
                            value={cappedProgress(usage.activeSeats, limits.seatCapacity)}
                        />
                    )}
                </div>
            )}
        </section>
    );
};

export default BillingPlanSummary;
