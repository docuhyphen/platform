import {makeStyles, tokens} from '@fluentui/react-components';

export const useOrganizationSequencesTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        padding: `0 ${tokens.spacingHorizontalXS}`,
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    descriptionText: {
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
});
