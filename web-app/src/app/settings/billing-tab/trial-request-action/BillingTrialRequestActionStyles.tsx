import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useBillingTrialRequestActionStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        ...shorthands.gap(tokens.spacingVerticalS),
    },
    feedback: {width: "100%"},
});
