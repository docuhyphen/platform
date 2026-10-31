import {makeStyles, tokens} from '@fluentui/react-components';

export const useSchemaSummaryCardStyles = makeStyles({
    card: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: tokens.spacingHorizontalL,
        flexWrap: 'wrap',
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXL}`,
        borderRadius: tokens.borderRadiusXLarge,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    titleBlock: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalSNudge,
        minWidth: 0,
    },
    title: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
        overflowWrap: 'anywhere',
    },
    badgeRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
        alignItems: 'center',
        flexWrap: 'wrap',
    },
});
