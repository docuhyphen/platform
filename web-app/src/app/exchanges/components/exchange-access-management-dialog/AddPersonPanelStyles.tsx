import {makeStyles, tokens} from '@fluentui/react-components';

export const useAddPersonPanelStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingBottom: tokens.spacingVerticalL,
    },
    topBar: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
    },
    form: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalM,
    },
    row: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
    },
    personField: {
        flex: '1',
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
