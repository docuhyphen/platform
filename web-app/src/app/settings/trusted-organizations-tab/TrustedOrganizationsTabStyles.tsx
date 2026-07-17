import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrustedOrganizationsTabStyles = makeStyles({
    root: {
        display: "grid",
        gridTemplateRows: "auto auto 1fr",
        gap: tokens.spacingVerticalM,
        minHeight: 0,
        height: "100%",
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    content: {
        display: "grid",
        gridTemplateColumns: "minmax(16rem, 0.8fr) minmax(20rem, 1.2fr)",
        gap: tokens.spacingHorizontalL,
        minHeight: 0,
        "@media (max-width: 760px)": {
            gridTemplateColumns: "1fr",
        },
    },
    empty: {
        display: "grid",
        placeItems: "center",
        textAlign: "center",
        minHeight: "12rem",
        ...shorthands.padding(tokens.spacingVerticalXL, tokens.spacingHorizontalL),
        color: tokens.colorNeutralForeground2,
    },
    error: {
        marginBottom: tokens.spacingVerticalS,
    },
});
