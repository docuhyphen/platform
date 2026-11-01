import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepCardStyles = makeStyles({
    card: {
        minWidth: 0,
    },

    cardBody: {
        paddingTop: tokens.spacingVerticalM,
        paddingRight: tokens.spacingHorizontalL,
        paddingBottom: tokens.spacingVerticalL,
        paddingLeft: tokens.spacingHorizontalL,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    fieldGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
});
