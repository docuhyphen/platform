import {makeStyles, tokens} from "@fluentui/react-components";

export const useResponseRequirementHeaderStyles = makeStyles({
    header: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    promptLine: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    prompt: {
        fontWeight: tokens.fontWeightSemibold,
        overflowWrap: "anywhere",
    },
    help: {
        color: tokens.colorNeutralForeground2,
    },
});
