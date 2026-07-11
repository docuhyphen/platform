import {tokens, makeStyles} from "@fluentui/react-components";

export const useIndividualOnboardingFormStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        flex: 1,
    },
    radioLabel: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
    },
});