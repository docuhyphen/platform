import {makeStyles, tokens} from '@fluentui/react-components';

export const useHelpGuideRoleCardStyles = makeStyles({
    roleCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
    },

    roleHeader: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
});
