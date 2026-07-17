import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrustedRelationshipDetailStyles = makeStyles({
    root: {
        display: "grid",
        alignContent: "start",
        gap: tokens.spacingVerticalM,
        overflowY: "auto",
        ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalL),
        backgroundColor: tokens.colorNeutralBackground1,
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        ...shorthands.border(tokens.strokeWidthThin, "solid", tokens.colorNeutralStroke2),
        "@media (max-width: 760px)": {
            ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
        },
    },
    summary: {
        display: "grid",
        gap: tokens.spacingVerticalXS,
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    warning: {
        color: tokens.colorPaletteDarkOrangeForeground2,
    },
    policies: {
        display: "grid",
        gap: tokens.spacingVerticalM,
    },
});
