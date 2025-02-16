import {makeStyles, tokens, typographyStyles,} from "@fluentui/react-components";

export const useSharingSessionStyles = makeStyles({

    caption2: typographyStyles.caption2,
    caption1: typographyStyles.caption1,
    body1Strong: typographyStyles.body1Strong,

    skeletonRecipientEmail: {
        width: "150px",
    },

    skeletonSessionName: {
        flex: 1,
        marginRight: "10px",
    },

    skeletonCreatedDate: {
        width: "50px",
        marginRight: "10px",
    },

    skeletonSessionDescription: {
        flex: "1",
        marginRight: "10px",
    },
});