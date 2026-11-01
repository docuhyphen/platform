import {makeStyles, tokens} from '@fluentui/react-components';

export const usePdfPageItemStyles = makeStyles({
    pdfPageItem: {
        width: "fit-content",
    },

    pdfPagePlaceholder: {
        width: "var(--pdf-page-placeholder-width, 612px)",
        height: "var(--pdf-page-placeholder-height, 792px)",
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground3,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusSmall,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },

    pdfPageAnimated: {
        "& canvas": {
            transitionProperty: "width, height, transform",
            transitionDuration: "180ms",
            transitionTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
            transformOrigin: "top center",
            willChange: "width, height, transform",
        },
        "& .react-pdf__Page__textContent": {
            transition: "transform 180ms cubic-bezier(0.2, 0, 0, 1)",
            transformOrigin: "top center",
        },
        "& .react-pdf__Page__annotations": {
            transition: "transform 180ms cubic-bezier(0.2, 0, 0, 1)",
            transformOrigin: "top center",
        },
    },
});
