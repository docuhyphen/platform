import {TableHeaderCell, Text} from "@fluentui/react-components";
import type {BillingFrequency, PricingPlan} from "./PricingPageData.ts";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

interface PricingPlanHeaderCellProps
{
    plan: PricingPlan;
    billingFrequency: BillingFrequency;
}

export function PricingPlanHeaderCell({plan, billingFrequency}: PricingPlanHeaderCellProps)
{
    const styles = usePricingPageStyles();
    const isAnnual = billingFrequency === "annual";

    return (
        <TableHeaderCell id={`pricing-plan-header-${plan.name}`}
                         className={styles.planHeader}>
            <div id={`pricing-plan-heading-${plan.name}`}
                 className={styles.planHeading}>
                <div id={`pricing-plan-name-row-${plan.name}`}
                     className={styles.planNameRow}>
                    <Text id={`pricing-plan-name-${plan.name}`}
                          className={styles.planName}>{plan.name}</Text>
                </div>
                <Text id={`pricing-plan-description-${plan.name}`}
                      className={styles.planDescription}
                      align="center">
                    {plan.description}
                </Text>
                <div id={`pricing-plan-price-row-${plan.name}`}
                     className={styles.priceRow}>
                    <Text id={`pricing-plan-price-${plan.name}`}
                          className={styles.price}>
                        {isAnnual ? plan.annualPrice : plan.monthlyPrice}
                    </Text>
                    <Text id={`pricing-plan-cadence-${plan.name}`}
                          className={styles.cadence}>{plan.cadence}</Text>
                </div>
                <Text id={`pricing-plan-billing-detail-${plan.name}`}
                      className={styles.billingDetail}
                      align={"center"}>
                    {isAnnual ? plan.annualDetail : plan.monthlyDetail}
                </Text>
            </div>
        </TableHeaderCell>
    );
}
