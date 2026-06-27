import {Badge, Text} from '@fluentui/react-components';
import {
    BuildingFilled,
    CreditCardPersonFilled,
    DataBarVerticalFilled,
    DocumentBulletListFilled,
    ReceiptFilled,
    ShieldLockFilled,
} from '@fluentui/react-icons';
import {useBillingTabStyles} from './BillingTabStyles.tsx';

interface Feature {
    icon: React.ReactNode;
    title: string;
    description: string;
}

const FEATURES: Feature[] = [
    {
        icon: <BuildingFilled/>,
        title: 'Subscription Plans',
        description: 'View and manage your organisation\'s current plan and available tiers.',
    },
    {
        icon: <DocumentBulletListFilled/>,
        title: 'Invoice History',
        description: 'Download and review itemised invoices for all billing periods.',
    },
    {
        icon: <CreditCardPersonFilled/>,
        title: 'Payment Methods',
        description: 'Securely manage cards and bank accounts on file.',
    },
    {
        icon: <ShieldLockFilled/>,
        title: 'Compliance & Audit',
        description: 'Maintain a complete, auditable record of all billing activity.',
    },
];

const BillingTab = () =>
{
    const styles = useBillingTabStyles();

    return (
        <div className={styles.container}>
            <div className={styles.heroIcon}>
                <ReceiptFilled/>
            </div>

            <div className={styles.headingGroup}>
                <Badge appearance="tint" color="brand" size="large">
                    Coming Soon
                </Badge>
                <Text as="h2" size={700} weight="semibold">
                    Billing &amp; Subscription
                </Text>
                <Text size={300} className={styles.tagline} align={"center"}>
                    Comprehensive financial visibility and control for your organisation
                    is on the horizon.
                </Text>
            </div>

            <div className={styles.featureGrid}>
                {FEATURES.map(feature => (
                    <div key={feature.title} className={styles.featureCard}>
                        <div className={styles.featureCardHeader}>
                            <span className={styles.featureIcon}>{feature.icon}</span>
                            <Text weight="semibold" size={300}>{feature.title}</Text>
                        </div>
                        <Text size={200} className={styles.featureDescription}>
                            {feature.description}
                        </Text>
                    </div>
                ))}
            </div>

            <Text size={200} className={styles.footer}>
                For early access or enterprise pricing, contact your account manager.
            </Text>
        </div>
    );
};

export default BillingTab;
