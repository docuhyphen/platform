import {makeStyles, tokens} from "@fluentui/react-components";

export const useBlueprintDetailsTabStyles = makeStyles({
    accordionPanelContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        paddingBottom: tokens.spacingVerticalS,
    },
});
