import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useTrustedParticipantPanelStyles = makeStyles({
    form: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalM,
    },
    role: {
        maxWidth: '20rem',
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
