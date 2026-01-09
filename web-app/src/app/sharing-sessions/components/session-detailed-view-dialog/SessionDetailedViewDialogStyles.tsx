import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionDDetailedViewDialogStyles = makeStyles({
    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px"
    },

    sessionStatuses: {
        display: "flex",
        gap: "8px"
    },
    sessionStatus: {
        flex: "1",
        border: "2px dotted #E1E1E1",
        textAlign: "center",
        opacity: 0.5,
        borderRadius: "4px",
        padding: "4px 0px"
    },
    sessionCurrentStatus: {
        opacity: "1"
    },
    sessionStatusINITIATED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteGreenForeground1
    },

    sessionStatusACCEPTED_STARTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteBlueForeground2
    },

    sessionStatusENDED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorNeutralForeground4
    },

    sessionStatusREJECTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteRedForeground1
    },
});