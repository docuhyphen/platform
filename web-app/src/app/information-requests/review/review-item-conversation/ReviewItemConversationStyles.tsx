import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewItemConversationStyles = makeStyles({
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    detail: {
        color: tokens.colorNeutralForeground2,
    },
});
