import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepUpModalStyles = makeStyles({
    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        margin: "8px 0",
    },
    infoText: {
        color: tokens.colorPaletteGreenForeground1,
    },
});
