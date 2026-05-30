import {makeStyles, tokens} from '@fluentui/react-components';

export const useSessionDocumentPreviewerStyles = makeStyles({
    documentName: {
        display: "flex",
        flexDirection: "column",
    },

    previewContainer: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
        background: tokens.colorNeutralBackground1,
        overflow: "hidden",
        maxWidth: "100%",
        margin: "0 auto",
        minHeight: 0,
    },

    previewHeader: {
        display: "flex",
        flexDirection: "column",
        padding: "8px",
        marginBottom: "8px",
        justifyContent: "center",
        alignItems: "center",
    },

    previewHeaderActions: {
        display: "flex",
        flexDirection: "row-reverse",
        justifyContent: "space-between",
        flex: 1,
    },

    pdfDocumentContainer: {
        flex: "1",
        background: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
        minWidth: "480px",
        minHeight: 0,
        padding: "16px",
        boxSizing: "border-box"
    },

    pdfDocument: {},

    enlargedPreviewContainer: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        width: "100%",
        height: "100%",
        boxSizing: "border-box",
        background: tokens.colorBackgroundOverlay,
        zIndex: 9999,
        padding: "16px",
        backgroundPosition: "relative",
        gap: "4px",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-around",
        flex: 1,
        transformOrigin: "center center",
        animationName: {
            from: {
                opacity: 0,
                transform: "scale(0.985)",
            },
            to: {
                opacity: 1,
                transform: "scale(1)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewContainerClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "scale(1)",
            },
            to: {
                opacity: 0,
                transform: "scale(0.985)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewHeader: {
        zIndex: 9999,
        margin: "0 auto",
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground1,
        width: "100%",
        display: "flex",
        justifyContent: "space-between",
        padding: "16px",
        borderRadius: "4px",
        animationName: {
            from: {
                opacity: 0,
                transform: "translateY(-8px)",
            },
            to: {
                opacity: 1,
                transform: "translateY(0)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewHeaderClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "translateY(0)",
            },
            to: {
                opacity: 0,
                transform: "translateY(-8px)",
            },
        },
        animationDuration: "180ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewHeaderActions: {
        display: "flex",
        alignItems: "center",
        gap: "4px"
    },

    pagesInputContainer: {
        display: "flex",
        alignItems: "center",
        gap: "2px",
    },

    enlargedPdfDocumentContainer: {
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground1,
        margin: "0 auto",
        flex: 1,
        overflow: "hidden",
        borderRadius: "4px",
        animationName: {
            from: {
                opacity: 0,
                transform: "translateY(8px)",
            },
            to: {
                opacity: 1,
                transform: "translateY(0)",
            },
        },
        animationDuration: "260ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPdfDocumentContainerClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "translateY(0)",
            },
            to: {
                opacity: 0,
                transform: "translateY(8px)",
            },
        },
        animationDuration: "200ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPdfDocument: {
        boxSizing: "border-box",
    },

    pagesInput: {
        "& input": {
            width: "50px",
            textAlign: "right",
        },
    },

    pagesInputAfter: {
        whiteSpace: "nowrap",
        color: tokens.colorNeutralForeground2,
    },

    pdfPagesStack: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        alignItems: "center",
    },

    pdfPageItem: {
        width: "fit-content",
    },

    enlargedReaderLayout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "stretch",
        height: "100%",
        width: "100%",
        minHeight: 0,
        minWidth: 0,
        overflow: "hidden",
    },

    thumbnailSidebar: {
        width: "180px",
        minWidth: "180px",
        flexShrink: 0,
        borderRight: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,
        overflowY: "auto",
        overflowX: "hidden",
        padding: "10px 8px",
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: "10px",
        zIndex: 1,
        boxShadow: `inset -1px 0 0 ${tokens.colorNeutralStroke2}`,
    },

    thumbnailSidebarEmpty: {
        padding: "8px 4px",
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
    },

    thumbnailPageButton: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "6px",
        backgroundColor: tokens.colorNeutralBackground1,
        padding: "6px",
        cursor: "pointer",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: "4px",
        transition: "border-color 140ms ease, box-shadow 140ms ease",
        "&:hover": {
            boxShadow: `0 0 0 1px ${tokens.colorBrandStroke1}`,
        },
    },

    thumbnailPageButtonActive: {
        boxShadow: `0 0 0 1px ${tokens.colorBrandStroke1}`,
    },

    thumbnailPageNumber: {
        color: tokens.colorNeutralForeground2,
    },

    thumbnailPagePreview: {
        width: "100%",
        display: "flex",
        justifyContent: "center",
    },

    mainDocumentPane: {
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        overflow: "auto",
        padding: "16px",
        boxSizing: "border-box",
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

    pdfLoadingContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "10px",
        minHeight: "240px",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: "6px",
        backgroundColor: tokens.colorNeutralBackground1,
    },

    pdfLoadingText: {
        color: tokens.colorNeutralForeground2,
    },

    previewEmptyState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "10px",
        minHeight: "280px",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: "6px",
        backgroundColor: tokens.colorNeutralBackground1,
        padding: "20px",
        textAlign: "center",
    },

    previewEmptySubText: {
        color: tokens.colorNeutralForeground2,
    },
});
