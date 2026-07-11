import {makeStyles, tokens} from '@fluentui/react-components';

export const useBillingTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: tokens.spacingHorizontalXXXL,
        padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL}) ${tokens.spacingHorizontalL} calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL})`,
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
        gap: tokens.spacingHorizontalM,
    },
    tagline: {
        color: tokens.colorNeutralForeground2,
        maxWidth: '30rem',
        lineHeight: '1.6',
    },
    featureGrid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gap: tokens.spacingHorizontalL,
        width: '100%',
        textAlign: 'left',
        '@media (max-width: 480px)': {
            gridTemplateColumns: '1fr',
        },
    },
    featureCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalSNudge,
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalL}`,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    featureCardHeader: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
        color: tokens.colorNeutralForeground1,
    },
    featureIcon: {
        fontSize: '1.125rem',
        color: tokens.colorBrandForeground1,
        flexShrink: 0,
    },
    featureDescription: {
        color: tokens.colorNeutralForeground3,
        paddingLeft: tokens.spacingHorizontalXXL,
    },
    footer: {
        color: tokens.colorNeutralForeground3,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        paddingTop: tokens.spacingVerticalXXL,
        width: '100%',
    },
});
