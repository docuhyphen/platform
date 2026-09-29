import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintInformationRequestTabStyles = makeStyles({
    tab: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        paddingTop: tokens.spacingVerticalM,
    },
    pinned: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
