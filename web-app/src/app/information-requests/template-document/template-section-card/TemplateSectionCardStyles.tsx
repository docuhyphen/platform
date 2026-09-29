import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateSectionCardStyles = makeStyles({
    card: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalM}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
    },
    header: {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    heading: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        minWidth: 0,
    },
    key: {
        color: tokens.colorNeutralForeground3,
        fontFamily: tokens.fontFamilyMonospace,
        fontSize: tokens.fontSizeBase200,
        overflowWrap: "anywhere",
    },
    help: {
        color: tokens.colorNeutralForeground2,
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
    requirements: {
        listStyleType: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    addButton: {
        alignSelf: "flex-start",
    },
});
