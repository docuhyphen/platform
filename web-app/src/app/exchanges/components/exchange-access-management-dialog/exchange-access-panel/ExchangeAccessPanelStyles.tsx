import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useExchangeAccessPanelStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalMNudge),
        marginTop: tokens.spacingVerticalS,
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
    entries: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
});
