import {Caption1, ProgressBar} from "@fluentui/react-components";
import {
    PlanCode,
    SubscriptionLimitsDto,
    SubscriptionOwnerType,
    SubscriptionUsageDto,
} from "../../../models/models.tsx";

interface BillingUsageSummaryProps
{
    planCode: PlanCode;
    ownerType: SubscriptionOwnerType;
    limits: SubscriptionLimitsDto;
    usage: SubscriptionUsageDto;
    usageRowClassName: string;
    usageLabelsClassName: string;
}

const cappedProgress = (current?: number | null, limit?: number | null): number =>
    limit && limit > 0 ? Math.min((current ?? 0) / limit, 1) : 0;

const BillingUsageSummary = ({
    planCode,
    ownerType,
    limits,
    usage,
    usageRowClassName,
    usageLabelsClassName,
}: BillingUsageSummaryProps) =>
{
    const showFreeUsage = planCode === PlanCode.FREE;
    const showSeatUsage = ownerType === SubscriptionOwnerType.ORGANIZATION;
    return (
        <>
            {showFreeUsage && limits.maxNewExchangesPerCalendarMonth != null && (
                <div
                    id={"settings-billing-monthly-exchange-usage"}
                    className={usageRowClassName}>
                    <div
                        id={"settings-billing-monthly-exchange-labels"}
                        className={usageLabelsClassName}>
                        <Caption1 id={"settings-billing-monthly-exchange-label"}>Exchanges this month</Caption1>
                        <Caption1 id={"settings-billing-monthly-exchange-value"}>
                            {usage.newExchangesThisPeriod ?? 0} / {limits.maxNewExchangesPerCalendarMonth}
                        </Caption1>
                    </div>
                    <ProgressBar
                        id={"settings-billing-monthly-exchange-progress"}
                        value={cappedProgress(usage.newExchangesThisPeriod, limits.maxNewExchangesPerCalendarMonth)}/>
                </div>
            )}
            {showFreeUsage && limits.maxOpenExchanges != null && (
                <div
                    id={"settings-billing-open-exchange-usage"}
                    className={usageRowClassName}>
                    <div
                        id={"settings-billing-open-exchange-labels"}
                        className={usageLabelsClassName}>
                        <Caption1 id={"settings-billing-open-exchange-label"}>Open Exchanges</Caption1>
                        <Caption1 id={"settings-billing-open-exchange-value"}>
                            {usage.openExchanges ?? 0} / {limits.maxOpenExchanges}
                        </Caption1>
                    </div>
                    <ProgressBar
                        id={"settings-billing-open-exchange-progress"}
                        value={cappedProgress(usage.openExchanges, limits.maxOpenExchanges)}/>
                </div>
            )}
            {showSeatUsage && (
                <div
                    id={"settings-billing-seat-usage"}
                    className={usageRowClassName}>
                    <div
                        id={"settings-billing-seat-labels"}
                        className={usageLabelsClassName}>
                        <Caption1 id={"settings-billing-seat-label"}>Active seats</Caption1>
                        <Caption1 id={"settings-billing-seat-value"}>
                            {usage.activeSeats ?? 0} / {limits.seatCapacity ?? "Uncapped"}
                        </Caption1>
                    </div>
                    {limits.seatCapacity != null && (
                        <ProgressBar
                            id={"settings-billing-seat-progress"}
                            value={cappedProgress(usage.activeSeats, limits.seatCapacity)}/>
                    )}
                </div>
            )}
        </>
    );
};

export default BillingUsageSummary;
