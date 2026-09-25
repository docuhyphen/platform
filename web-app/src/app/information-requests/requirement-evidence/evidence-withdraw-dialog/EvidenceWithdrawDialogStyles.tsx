import {makeStyles, tokens} from "@fluentui/react-components";

export const useEvidenceWithdrawDialogStyles = makeStyles({
    surface: {
        maxWidth: `min(480px, calc(100vw - (${tokens.spacingHorizontalXXL} * 2)))`,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
