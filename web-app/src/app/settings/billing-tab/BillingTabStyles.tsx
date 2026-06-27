import {makeStyles, tokens} from '@fluentui/react-components';

export const useBillingTabStyles = makeStyles({
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
