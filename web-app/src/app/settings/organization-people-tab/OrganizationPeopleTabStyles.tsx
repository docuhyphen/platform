import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationPeopleTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        width: "100%",
        height: "100%",
        minHeight: 0,
        overflow: "hidden"
    },
    tableScroll: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "auto",
        overscrollBehavior: "contain"
    },
    paginationFooter: {
        display: "flex",
        flexShrink: 0,
        paddingTop: "8px",
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`
    },
    loading: {
        display: "flex",
        justifyContent: "center",
        padding: "20px"
    },
    error: {
        color: tokens.colorStatusDangerForeground1,
        ...shorthands.padding("10px"),
        backgroundColor: tokens.colorStatusDangerBackground1,
        ...shorthands.borderRadius("4px"),
        flexShrink: 0
    }
});
