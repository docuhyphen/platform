import {makeStyles, tokens} from '@fluentui/react-components';

export const useReplacePrimaryRecipientPanelStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
    },
    topBar: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: 'flex',
        justifyContent: 'flex-end',
        gap: tokens.spacingHorizontalS,
    },
});
