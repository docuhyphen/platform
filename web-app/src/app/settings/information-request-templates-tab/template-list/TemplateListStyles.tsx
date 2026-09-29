import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateListStyles = makeStyles({
    list: {
        listStyleType: "none",
        margin: 0,
        padding: 0,
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(18rem, 1fr))",
        gap: tokens.spacingHorizontalM,
    },
    card: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    main: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
    },
    key: {
        color: tokens.colorNeutralForeground3,
        fontFamily: tokens.fontFamilyMonospace,
        fontSize: tokens.fontSizeBase200,
        overflowWrap: "anywhere",
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    badges: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
        paddingTop: tokens.spacingVerticalXXS,
    },
});
