import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useAppAdminsTabStyles = makeStyles({
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
    table: {
        width: '100%',
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
    actionsCell: {
        width: '90px',
        minWidth: '90px',
        maxWidth: '90px',
    },
});
