import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListSkeletonStyles = makeStyles({
    skeletonRecipientEmail: {
        width: "150px",
    },

    skeletonExchangeName: {
        flex: 1,
        marginRight: tokens.spacingHorizontalMNudge,
    },

    skeletonCreatedDate: {
        width: "50px",
        marginRight: tokens.spacingHorizontalMNudge,
    },

    skeletonExchangeDescription: {
        flex: "1",
        marginRight: tokens.spacingHorizontalMNudge,
    },
});
