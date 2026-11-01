import {makeStyles, tokens} from '@fluentui/react-components';

export const useOrganizationVariablesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalM,
        padding: `0 ${tokens.spacingHorizontalXS}`,
    },
    descriptionText: {
        color: tokens.colorNeutralForeground3,
    },
});
