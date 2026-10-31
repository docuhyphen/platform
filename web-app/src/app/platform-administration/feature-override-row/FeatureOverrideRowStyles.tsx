import {makeStyles, tokens} from "@fluentui/react-components";

export const useFeatureOverrideRowStyles = makeStyles({
    entitlementRow: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) auto auto",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    entitlementName: {
        minWidth: 0,
        overflowWrap: "anywhere",
    },
});
