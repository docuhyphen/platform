import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useExchangeAccessPanelStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('10px'),
        marginTop: '8px',
    },
    toolbar: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        ...shorthands.gap('8px'),
    },
    addForm: {
        display: 'flex',
        flexDirection: "column",
        gap: "8px"
    },
    addFormRow1: {
        display: "flex",
        gap: "8px"
    },
    addFormPersonField: {
        flex: "1"
    },

    addConstraintSection: {
        gridColumn: '1 / -1',
    },
    constraintsEditor: {
        ...shorthands.margin('2px', '0', '0', '0'),
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('6px'),
    },
    constraintChips: {
        display: 'flex',
        ...shorthands.gap('6px'),
        flexWrap: 'wrap',
    },
    entries: {
        display: 'flex',
        flexDirection: 'column',
        gap: "16px"
    },
    row: {
        ...shorthands.border('1px', 'solid', tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
        ...shorthands.padding('8px'),
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('8px'),
        minWidth: 0,
        position: 'relative',
    },
    rowTop: {
        display: 'flex',
        alignItems: 'center',
        ...shorthands.gap('8px'),
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
        gap: "2xp"
    },
    // roleField: {
    //     width: '120px',
    //     minWidth: '100px',
    // },
    rowBottom: {
        display: 'flex',
        alignItems: 'center',
        ...shorthands.gap('8px'),
        flexWrap: 'wrap',
    },
    actionButton: {
        flexShrink: 0,
    },
    floatingStatus: {
        position: 'absolute',
        top: '-8px',
        left: '-8px',
        zIndex: 1
    },
    details: {
        ...shorthands.borderTop('1px', 'solid', tokens.colorNeutralStroke2),
        ...shorthands.padding('8px', '0', '0', '0'),
        display: 'grid',
        gridTemplateColumns: '1fr',
        ...shorthands.gap('8px'),
    },
    detailsMeta: {
        color: tokens.colorNeutralForeground3,
        display: 'flex',
        flexWrap: 'wrap',
        ...shorthands.gap('10px'),
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
});

