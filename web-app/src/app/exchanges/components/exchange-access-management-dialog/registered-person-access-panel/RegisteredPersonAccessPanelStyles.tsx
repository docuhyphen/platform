import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useRegisteredPersonAccessPanelStyles = makeStyles({
    form: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalM,
    },
    row: {
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) minmax(12rem, auto)',
        gap: tokens.spacingHorizontalS,
        '@media (max-width: 600px)': {
            gridTemplateColumns: '1fr',
        },
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: 'flex',
        justifyContent: 'flex-end',
        flexWrap: 'wrap',
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
