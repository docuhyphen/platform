import {makeStyles, tokens} from "@fluentui/react-components";

export const useConditionPredicateRowStyles = makeStyles({
    row: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1.5fr) auto",
        alignItems: "end",
        gap: tokens.spacingHorizontalS,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        "@media screen and (max-width: 620px)": {
            gridTemplateColumns: "1fr",
        },
    },
    remove: {
        justifySelf: "end",
    },
});
