import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSessionsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        width: "100%",
        minWidth: 0,
        height: "100%",
        maxWidth: "100%",
    },

    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },

    summaryBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    subtleText: {
        color: tokens.colorNeutralForeground3,
    },

    tableWrapper: {
        width: "100%",
        overflowX: "auto",
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusXLarge),
    },

    table: {
        minWidth: "760px",
        width: "100%",
        backgroundColor: tokens.colorNeutralBackground1,
    },

    deviceColumn: {
        minWidth: "180px",
    },

    ipColumn: {
        minWidth: "110px",
    },

    dateColumn: {
        minWidth: "125px",
    },

    statusColumn: {
        minWidth: "110px",
    },

    actionsColumn: {
        minWidth: "120px",
        textAlign: "right",
    },

    currentRow: {
        backgroundColor: tokens.colorBrandBackground2,
    },

    currentDeviceCell: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    deviceName: {
        color: tokens.colorNeutralForeground1,
    },

    metaCell: {
        color: tokens.colorNeutralForeground2,
        whiteSpace: "normal",
        lineHeight: tokens.lineHeightBase200,
    },

    actionsCell: {
        textAlign: "right",
        whiteSpace: "nowrap",
    },

    statusCell: {
        whiteSpace: "nowrap",
    },
});
