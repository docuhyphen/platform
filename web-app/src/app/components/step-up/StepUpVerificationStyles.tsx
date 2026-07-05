import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepUpVerificationStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        margin: "8px 0",
    },
    infoText: {
        color: tokens.colorPaletteGreenForeground1,
    },
    errorText: {
        color: tokens.colorStatusDangerForeground1,
    },
    resendRow: {
        display: "flex",
        justifyContent: "start",
    },
});
