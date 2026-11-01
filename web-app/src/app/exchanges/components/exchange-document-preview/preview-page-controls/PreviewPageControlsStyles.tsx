import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewPageControlsStyles = makeStyles({
    pagesInputContainer: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        flexShrink: 0,
    },

    pagesInput: {
        "& input": {
            width: "50px",
            textAlign: "right",
        },
        "@media (max-width: 768px)": {
            "& input": {
                width: "36px",
            },
        },
    },

    pagesInputAfter: {
        whiteSpace: "nowrap",
        color: tokens.colorNeutralForeground2,
    },
});
