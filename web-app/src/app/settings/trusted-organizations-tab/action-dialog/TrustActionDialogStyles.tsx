import {makeStyles, tokens} from "@fluentui/react-components";

export const useTrustActionDialogStyles = makeStyles({
    content: {
        display: "grid",
        gap: tokens.spacingVerticalM,
        minWidth: "min(28rem, 80vw)",
        "@media (max-width: 480px)": {
            minWidth: 0,
        },
    },
});
