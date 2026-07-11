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
    addForm: {
        display: 'flex',
        flexDirection: "column",
        gap: tokens.spacingHorizontalS
    },
    addFormRow1: {
        display: "flex",
        gap: tokens.spacingHorizontalS
    },
    addFormPersonField: {
        flex: "1"
    },

    addConstraintSection: {
        gridColumn: '1 / -1',
    },
    constraintsEditor: {
        ...shorthands.margin(tokens.spacingVerticalXXS, '0', '0', '0'),
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalSNudge),
    },
    constraintChips: {
        display: 'flex',
        ...shorthands.gap(tokens.spacingHorizontalSNudge),
        flexWrap: 'wrap',
    },
    entries: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL
    },
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
    detailsRow1: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center"
    },
    accessSave: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "end"
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
    // roleField: {
    //     width: '120px',
    //     minWidth: '100px',
    // },
    rowBottom: {
        display: 'flex',
        alignItems: 'center',
        ...shorthands.gap(tokens.spacingHorizontalS),
        flexWrap: 'wrap',
    },
    actionButton: {
        flexShrink: 0,
    },
    floatingStatus: {
        position: 'absolute',
        top: `-${tokens.spacingVerticalS}`,
        left: `-${tokens.spacingHorizontalS}`,
        zIndex: 1
    },
    details: {
        ...shorthands.borderTop('1px', 'solid', tokens.colorNeutralStroke2),
        ...shorthands.padding(tokens.spacingVerticalS, '0', '0', '0'),
        display: 'grid',
        gridTemplateColumns: '1fr',
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
    detailsMeta: {
        color: tokens.colorNeutralForeground3,
        display: 'flex',
        flexWrap: 'wrap',
        ...shorthands.gap(tokens.spacingHorizontalMNudge),
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
});

