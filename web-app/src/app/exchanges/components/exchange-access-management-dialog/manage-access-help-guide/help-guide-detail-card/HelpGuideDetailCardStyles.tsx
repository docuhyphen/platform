import {makeStyles, tokens} from '@fluentui/react-components';

export const useHelpGuideDetailCardStyles = makeStyles({
    constraintCard: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
    },
});
