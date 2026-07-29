import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        flex: 1,
        width: "100%",
        height: "100%",
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
        ...shorthands.gap(tokens.spacingVerticalM),
    },
    loading: {
        display: "flex",
        flex: 1,
        minHeight: 0,
        alignItems: "center",
        justifyContent: "center",
        ...shorthands.padding(tokens.spacingVerticalXL),
    },
    feedback: {
        flexShrink: 0,
    },
    pagination: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        flexShrink: 0,
        ...shorthands.gap(tokens.spacingHorizontalS),
    },
});
