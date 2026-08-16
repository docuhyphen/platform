import {Caption1} from "@fluentui/react-components";

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

export const formatTrialEnd = (value?: string | null): string =>
{
    if (!value) return "Not set";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Not set";
    return new Intl.DateTimeFormat(undefined, {dateStyle: "medium"}).format(date);
};

export const trialDaysRemaining = (value?: string | null, now: number = Date.now()): number =>
{
    if (!value) return 0;
    const end = new Date(value).getTime();
    if (Number.isNaN(end)) return 0;
    return Math.max(0, Math.ceil((end - now) / MILLISECONDS_PER_DAY));
};

interface BillingTrialDetailsProps
{
    currentPeriodEnd?: string | null;
}

const BillingTrialDetails = ({currentPeriodEnd}: BillingTrialDetailsProps) =>
{
    const daysRemaining = trialDaysRemaining(currentPeriodEnd);
    return (
        <>
            <Caption1 id={"settings-billing-trial-end"}>
                Trial ends {formatTrialEnd(currentPeriodEnd)}
            </Caption1>
            <Caption1 id={"settings-billing-trial-days-remaining"}>
                {daysRemaining} {daysRemaining === 1 ? "day" : "days"} remaining
            </Caption1>
        </>
    );
};

export default BillingTrialDetails;
