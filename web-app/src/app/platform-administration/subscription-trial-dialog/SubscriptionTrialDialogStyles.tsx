import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSubscriptionTrialDialogStyles = makeStyles({
    dialogBody: {
        width: "min(520px, calc(100vw - 32px))",
    },
    content: {
        display: "flex",
        flexDirection: "column",
        ...shorthands.gap(tokens.spacingVerticalM),
    },
    fieldGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        ...shorthands.gap(tokens.spacingHorizontalM),
        "@media (max-width: 480px)": {
            gridTemplateColumns: "1fr",
        },
    },
    footer: {
        display: "flex",
        justifyContent: "flex-end",
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
