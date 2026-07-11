import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationGroupsToolbarStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexShrink: 0,
        backgroundColor: tokens.colorNeutralBackground1,
        "@media (max-width: 900px)": {
            alignItems: "stretch",
            flexDirection: "column"
        }
    },
    searchBox: {
        flex: 1,
        minWidth: "180px"
    },
    tools: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0
    },
    actionButton: {
        marginLeft: "auto",
        flexShrink: 0
    }
});
