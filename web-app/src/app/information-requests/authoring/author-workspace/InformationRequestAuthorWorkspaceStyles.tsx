import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestAuthorWorkspaceStyles = makeStyles({
    page: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        maxWidth: "1100px",
        width: "100%",
        marginLeft: "auto",
        marginRight: "auto",
        paddingTop: tokens.spacingVerticalL,
        paddingBottom: tokens.spacingVerticalXXL,
        paddingLeft: tokens.spacingHorizontalL,
        paddingRight: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        "@media (max-width: 640px)": {
            paddingLeft: tokens.spacingHorizontalM,
            paddingRight: tokens.spacingHorizontalM,
        },
    },
    columns: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 3fr) minmax(0, 2fr)",
        gap: tokens.spacingHorizontalXXL,
        "@media (max-width: 1024px)": {
            gridTemplateColumns: "minmax(0, 1fr)",
            rowGap: tokens.spacingVerticalXL,
        },
    },
});
