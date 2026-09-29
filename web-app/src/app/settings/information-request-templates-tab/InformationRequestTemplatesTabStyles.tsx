import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestTemplatesTabStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        height: "100%",
        minHeight: 0,
        width: "100%",
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        paddingInline: tokens.spacingHorizontalS,
        flexShrink: 0,
    },
    content: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        paddingInline: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalM,
        boxSizing: "border-box",
    },
});
