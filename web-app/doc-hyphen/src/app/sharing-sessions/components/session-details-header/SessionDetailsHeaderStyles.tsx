import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionDetailsHeaderStyles = makeStyles({

    container: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        background: "white",
        padding: "8px 16px",
        borderRadius: "4px",
        borderTop: "1px solid rgba(0, 0, 0, .2)",
        borderRight: "1px solid rgba(0, 0, 0, .2)",
        borderBottom: "1px solid rgba(0, 0, 0, .2)",
        boxShadow: "rgba(0, 0, 0, 0.12) 0px 0px 2px, rgba(0, 0, 0, 0.14) 0px 2px 4px",
    },
    
    header: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
    },

    headerLine1: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    headerLine1_2: {
        display: "flex",
        flexDirection: "row",
        gap: "8px"
    },

    headerLine2: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    headerLine3: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    containerStatusINITIATED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteGreenForeground1
    },

    containerStatusACCEPTED_STARTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteBlueForeground2
    },

    containerStatusENDED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorNeutralForeground4
    },

    containerStatusREJECTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteRedForeground1
    },

    actions: {
        display: "flex",
        gap: "8px",
    },
});