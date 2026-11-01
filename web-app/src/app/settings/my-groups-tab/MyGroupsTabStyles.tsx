import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useMyGroupsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalM),
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    loading: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalXXXL),
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
        ...shorthands.padding(tokens.spacingHorizontalS),
    },
});
