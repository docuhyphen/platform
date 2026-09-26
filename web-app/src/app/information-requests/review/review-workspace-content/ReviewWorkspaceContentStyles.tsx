import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewWorkspaceContentStyles = makeStyles({
    workspace: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        width: "100%",
        maxWidth: "1100px",
        minWidth: 0,
    },
    items: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
});
