import type {ReactNode} from 'react';
import {Badge, Text} from '@fluentui/react-components';
import {
    BuildingFilled,
    CreditCardPersonFilled,
    DataBarVerticalFilled,
    DocumentBulletListFilled,
    ReceiptFilled,
    ShieldLockFilled,
} from '@fluentui/react-icons';
import BillingPlanSummary from "./billing-plan-summary/BillingPlanSummary.tsx";
import {useBillingTabStyles} from './BillingTabStyles.tsx';

interface Feature
{
    id: string;
    icon: ReactNode;
    title: string;
    description: string;
}

const FEATURES: Feature[] = [
    {
        id: "subscription-plans",
        icon: <BuildingFilled id={"settings-billing-subscription-plans-source-icon"}/>,
        title: 'Subscription Plans',
        description: 'View your current plan and request an eligible trial.',
    },
    {
        id: "invoice-history",
        icon: <DocumentBulletListFilled id={"settings-billing-invoice-history-source-icon"}/>,
        title: 'Invoice History',
        description: 'Download and review itemised invoices for all billing periods.',
    },
    {
        id: "payment-methods",
        icon: <CreditCardPersonFilled id={"settings-billing-payment-methods-source-icon"}/>,
        title: 'Payment Methods',
        description: 'Securely manage cards and bank accounts on file.',
    },
    {
        id: "compliance-audit",
        icon: <ShieldLockFilled id={"settings-billing-compliance-audit-source-icon"}/>,
        title: 'Compliance & Audit',
        description: 'Maintain a complete, auditable record of all billing activity.',
    },
];

const BillingTab = () =>
{
    const styles = useBillingTabStyles();

    return (
        <div
            id={"settings-billing-tab"}
            className={styles.container}
        >
            <div
                id={"settings-billing-hero-icon"}
                className={styles.heroIcon}
            >
                <ReceiptFilled id={"settings-billing-receipt-icon"}/>
            </div>

            <div
                id={"settings-billing-heading-group"}
                className={styles.headingGroup}
            >
                <Badge
                    id={"settings-billing-coming-soon-badge"}
                    appearance="tint"
                    color="brand"
                    size="large"
                >
                    Coming Soon
                </Badge>
                <Text
                    id={"settings-billing-heading"}
                    as="h2"
                    size={700}
                    weight="semibold"
                >
                    Billing &amp; Subscription
                </Text>
                <Text
                    id={"settings-billing-tagline"}
                    size={300}
                    className={styles.tagline}
                    align={"center"}
                >
                    Review your current plan and request a trial. Paid billing management is coming.
                </Text>
            </div>

            <BillingPlanSummary/>

            <div
                id={"settings-billing-feature-grid"}
                className={styles.featureGrid}
            >
                {FEATURES.map(feature => (
                    <div
                        id={`settings-billing-${feature.id}-card`}
                        key={feature.title}
                        className={styles.featureCard}
                    >
                        <div
                            id={`settings-billing-${feature.id}-header`}
                            className={styles.featureCardHeader}
                        >
                            <span
                                id={`settings-billing-${feature.id}-icon`}
                                className={styles.featureIcon}
                            >
                                {feature.icon}
                            </span>
                            <Text
                                id={`settings-billing-${feature.id}-title`}
                                weight="semibold"
                                size={300}
                            >
                                {feature.title}
                            </Text>
                        </div>
                        <Text
                            id={`settings-billing-${feature.id}-description`}
                            size={200}
                            className={styles.featureDescription}
                        >
                            {feature.description}
                        </Text>
                    </div>
                ))}
            </div>

            <Text
                id={"settings-billing-footer"}
                size={200}
                className={styles.footer}
            >
                For early access or enterprise pricing, contact your account manager.
            </Text>
        </div>
    );
};

export default BillingTab;
