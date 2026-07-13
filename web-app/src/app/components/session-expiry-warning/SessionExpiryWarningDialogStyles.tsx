import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionExpiryWarningDialogStyles = makeStyles({
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    countdown: {
        color: tokens.colorPaletteRedForeground1,
    },
});
