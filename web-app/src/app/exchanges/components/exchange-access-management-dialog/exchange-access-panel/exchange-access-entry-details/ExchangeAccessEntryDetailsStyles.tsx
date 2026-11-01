import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useExchangeAccessEntryDetailsStyles = makeStyles({
    details: {
        ...shorthands.borderTop('1px', 'solid', tokens.colorNeutralStroke2),
        ...shorthands.padding(tokens.spacingVerticalS, '0', '0', '0'),
        display: 'grid',
        gridTemplateColumns: '1fr',
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
    constraintsEditor: {
        ...shorthands.margin(tokens.spacingVerticalXXS, '0', '0', '0'),
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalSNudge),
    },
    accessSave: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "end"
    },
    actionButton: {
        flexShrink: 0,
    },
});
