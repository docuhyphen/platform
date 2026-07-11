import {tokens, makeStyles} from "@fluentui/react-components";

export const useStepUpStyles = makeStyles({
    stepUpContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        alignItems: "center",
        marginTop: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL})`,
    },
});
