import {Badge, Text} from '@fluentui/react-components';
import {
    BuildingFilled,
    CreditCardPersonFilled,
    DataBarVerticalFilled,
    DocumentBulletListFilled,
    ReceiptFilled,
    ShieldLockFilled,
} from '@fluentui/react-icons';
import {makeStyles, tokens} from '@fluentui/react-components';

const useBillingTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '2.5rem',
        padding: '3rem 1rem 4rem',
        textAlign: 'center',
        maxWidth: '42rem',
        margin: '0 auto',
    },
    heroIcon: {
        fontSize: '4rem',
        color: tokens.colorBrandForeground1,
        lineHeight: 1,
    },
    headingGroup: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '0.75rem',
    },
    tagline: {
        color: tokens.colorNeutralForeground2,
        maxWidth: '30rem',
        lineHeight: '1.6',
    },
    featureGrid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gap: '1rem',
        width: '100%',
        textAlign: 'left',
        '@media (max-width: 480px)': {
            gridTemplateColumns: '1fr',
        },
    },
    featureCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.375rem',
        padding: '1rem 1.125rem',
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    featureCardHeader: {
        display: 'flex',
        alignItems: 'center',
        gap: '0.5rem',
        color: tokens.colorNeutralForeground1,
    },
    featureIcon: {
        fontSize: '1.125rem',
        color: tokens.colorBrandForeground1,
        flexShrink: 0,
    },
    featureDescription: {
        color: tokens.colorNeutralForeground3,
        paddingLeft: '1.625rem',
    },
    footer: {
        color: tokens.colorNeutralForeground3,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        paddingTop: '1.5rem',
        width: '100%',
    },
});

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
                <Text size={300} className={styles.tagline}>
                    Comprehensive financial visibility and control for your organisation
                    is on the horizon. Manage plans, track usage, and maintain a full
                    audit trail — all in one place.
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
