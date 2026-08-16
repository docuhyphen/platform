import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrialRequestDecisionDialogStyles = makeStyles({
    body: {width: "min(560px, calc(100vw - 32px))"},
    content: {display: "flex", flexDirection: "column", ...shorthands.gap(tokens.spacingVerticalM)},
    fields: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        ...shorthands.gap(tokens.spacingHorizontalM),
        "@media (max-width: 600px)": {gridTemplateColumns: "1fr"},
    },
    footer: {display: "flex", justifyContent: "flex-end", ...shorthands.gap(tokens.spacingHorizontalS)},
});
