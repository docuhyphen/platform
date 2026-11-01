import {makeStyles, tokens} from '@fluentui/react-components';

export const useSequenceCardStyles = makeStyles({
    sequenceCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: tokens.spacingHorizontalS,
    },
    sequenceCardInner: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
        flex: '1',
        minWidth: '0',
    },
    codeToken: {
        fontFamily: 'monospace',
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground2,
    },
    badgeRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalSNudge,
        flexWrap: 'wrap',
        alignItems: 'center',
    },
    nextText: {
        color: tokens.colorNeutralForeground3,
    },
});
