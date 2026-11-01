import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionTableRowStyles = makeStyles({
    deviceColumn: {
        minWidth: "180px",
    },

    ipColumn: {
        minWidth: "110px",
    },

    dateColumn: {
        minWidth: "125px",
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
