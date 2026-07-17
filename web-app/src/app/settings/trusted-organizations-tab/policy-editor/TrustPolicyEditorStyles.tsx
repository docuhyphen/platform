import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrustPolicyEditorStyles = makeStyles({
    root: {
        display: "grid",
        gap: tokens.spacingVerticalS,
        ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
        ...shorthands.border(tokens.strokeWidthThin, "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
    },
    switches: {
        display: "grid",
        gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        gap: tokens.spacingVerticalS,
        "@media (max-width: 600px)": {
            gridTemplateColumns: "1fr",
        },
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
    },
});
