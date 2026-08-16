import {Caption1, Link, Text} from "@fluentui/react-components";

const PRICING_URL = "https://www.docuhyphen.com/pricing";

interface BillingPlanHeaderProps
{
    planTitle: string;
    status: string;
    planHeaderClassName: string;
    planTitleClassName: string;
}

const BillingPlanHeader = ({
    planTitle,
    status,
    planHeaderClassName,
    planTitleClassName,
}: BillingPlanHeaderProps) => (
    <div
        id={"settings-billing-current-plan-header"}
        className={planHeaderClassName}
    >
        <div
            id={"settings-billing-current-plan-title"}
            className={planTitleClassName}
        >
            <Text
                id={"settings-billing-current-plan-name"}
                weight={"semibold"}
                size={500}
            >
                {planTitle}
            </Text>
            <Caption1 id={"settings-billing-current-plan-status"}>
                {status}
            </Caption1>
        </div>
        <Link
            id={"settings-billing-pricing-link"}
            href={PRICING_URL}
            target={"_blank"}
            rel={"noopener noreferrer"}
        >
            Compare plans and features
        </Link>
    </div>
);

export default BillingPlanHeader;
