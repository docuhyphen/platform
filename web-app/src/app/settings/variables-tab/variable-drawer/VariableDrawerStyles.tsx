import {makeStyles, tokens} from '@fluentui/react-components';

export const useVariableDrawerStyles = makeStyles({
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },
    buttonRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
        justifyContent: 'flex-end',
    },
});
