import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementEvidenceAttributesStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        paddingTop: tokens.spacingVerticalM,
    },
    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(4, minmax(0, 1fr))",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 820px)": {
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        },
        "@media screen and (max-width: 520px)": {
            gridTemplateColumns: "1fr",
        },
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    list: {
        listStyleType: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    item: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
    },
    newValue: {
        display: "flex",
        alignItems: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
