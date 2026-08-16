import {Caption1} from "@fluentui/react-components";
import {
    SubscriptionStatus,
} from "../../../models/models.tsx";
import {useCurrentSubscription} from "../../../../hooks/subscription/useCurrentSubscription.ts";
import BillingPlanHeader from "./BillingPlanHeader.tsx";
import BillingTrialDetails from "./BillingTrialDetails.tsx";
import BillingUsageSummary from "./BillingUsageSummary.tsx";
import {useBillingPlanSummaryStyles} from "./BillingPlanSummaryStyles.tsx";
import BillingTrialRequestAction from "../trial-request-action/BillingTrialRequestAction.tsx";

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

const BillingPlanSummary = () =>
{
    const styles = useBillingPlanSummaryStyles();
    const subscription = useCurrentSubscription();
    const limits = subscription?.limits ?? null;
    const usage = subscription?.usage ?? null;
    if (!subscription || !limits || !usage) return null;

    const isTrial = subscription.status === SubscriptionStatus.TRIALING;
    const planTitle = `${formatValue(subscription.planCode)} ${isTrial ? "trial" : "plan"}`;

    return (
        <section
            id={"settings-billing-current-plan"}
            className={styles.planCard}
            aria-label={"Current plan"}
        >
            <BillingPlanHeader
                planTitle={planTitle}
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
                {!isTrial && (
                    <Caption1 id={"settings-billing-current-plan-period"}>
                        Current period ends: {formatDate(subscription.currentPeriodEnd)}
                    </Caption1>
                )}
                {isTrial && <BillingTrialDetails currentPeriodEnd={subscription.currentPeriodEnd}/>}
            </div>
            <BillingUsageSummary
                planCode={subscription.planCode}
                ownerType={subscription.ownerType}
                limits={limits}
                usage={usage}
                usageRowClassName={styles.usageRow}
                usageLabelsClassName={styles.usageLabels}/>
            {!isTrial && <BillingTrialRequestAction ownerType={subscription.ownerType}/>}
        </section>
    );
};

export default BillingPlanSummary;
