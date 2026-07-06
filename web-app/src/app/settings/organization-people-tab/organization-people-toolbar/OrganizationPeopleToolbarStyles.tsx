import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationPeopleToolbarStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        gap: "12px",
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
        gap: "8px",
        flexShrink: 0
    },
    actionButton: {
        marginLeft: "auto",
        flexShrink: 0
    },
    filterPopover: {
        padding: "8px",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        minWidth: "220px",
        maxWidth: "300px"
    },
    filterSection: {
        display: "flex",
        flexDirection: "column",
        gap: "4px"
    },
    filterSectionTitle: {
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        paddingLeft: "2px"
    },
    filterSectionList: {
        maxHeight: "180px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column"
    },
    filterDivider: {
        border: "none",
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        margin: 0
    }
});
