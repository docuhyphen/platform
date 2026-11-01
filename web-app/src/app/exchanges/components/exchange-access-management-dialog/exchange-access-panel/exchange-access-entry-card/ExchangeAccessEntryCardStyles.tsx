import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useExchangeAccessEntryCardStyles = makeStyles({
    row: {
        ...shorthands.border('1px', 'solid', tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
        ...shorthands.padding(tokens.spacingHorizontalS),
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        minWidth: 0,
        position: 'relative',
    },
    rowTop: {
        display: 'flex',
        alignItems: 'center',
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
    nameCell: {
        flex: '1 1 240px',
        minWidth: 0,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        display: "flex",
        gap: tokens.spacingHorizontalXXS
    },
    actionButton: {
        flexShrink: 0,
    },
});
