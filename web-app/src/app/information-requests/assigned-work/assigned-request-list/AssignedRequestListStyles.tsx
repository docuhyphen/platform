import {makeStyles, tokens} from "@fluentui/react-components";

export const useAssignedRequestListStyles = makeStyles({
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        listStyleType: "none",
        margin: 0,
        padding: 0,
        minWidth: 0,
    },
});
