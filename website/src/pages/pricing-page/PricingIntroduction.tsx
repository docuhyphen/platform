import {Switch, Text, Title2} from "@fluentui/react-components";
import type {BillingFrequency} from "./PricingPageData.ts";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

interface PricingIntroductionProps
{
    billingFrequency: BillingFrequency;
    onBillingFrequencyChange: (billingFrequency: BillingFrequency) => void;
}

export function PricingIntroduction({billingFrequency, onBillingFrequencyChange}: PricingIntroductionProps)
{
    const styles = usePricingPageStyles();

    return (
        <section id={"pricing-introduction"}
                 className={styles.introduction}
                 aria-labelledby="pricing-page-title">
            <Title2 id={"pricing-page-title"}
                    className={styles.heading}>
                Simple pricing for secure document Exchanges
            </Title2>
            <Text id={"pricing-page-description"}
                  className={styles.description}
                  size={500}>
                Pay only for your team. Invite as many external clients as you need, and let them respond
                securely without creating an account.
            </Text>
            <div id={"pricing-billing-options"}
                 className={styles.billingOptions}
                 aria-label="Billing frequency">
                <Switch id={"pricing-billing-switch"}
                        checked={billingFrequency === "annual"}
                        label={billingFrequency === "annual"
                            ? "Annual billing, save up to 17%"
                            : "Month-to-month billing"}
                        labelPosition="after"
                        onChange={(_, data) => onBillingFrequencyChange(data.checked ? "annual" : "monthly")}/>
            </div>
        </section>
    );
}
