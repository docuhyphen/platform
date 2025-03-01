// SessionDocumentPreviewerStyles.tsx
import {makeStyles} from '@fluentui/react-components';

export const useSessionDocumentPreviewerStyles = makeStyles({

    documentName: {
        display: "flex",
        flexDirection: "column",
    },

    previewContainer: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
        background: "white",
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
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
        background: "white",
        border: "1px solid rgba(0, 0, 0, .1)",
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
        minWidth: "480px",
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
        background: "rgba(0, 0, 0, 0.8)",
        zIndex: 9999,
        padding: "16px",
        backgroundPosition: "relative",
        gap: "4px",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-around",
        flex: 1,
    },

    enlargedPreviewHeader: {
        maxWidth: "800px",
        zIndex: 9999,
        margin: "0 auto",
        boxSizing: "border-box",
        background: "white",
        width: "100%",
        display: "flex",
        justifyContent: "space-between",
        padding: "16px",
        borderRadius: "4px",
    },

    enlargedPreviewHeaderActions: {
        display: "flex",
        alignItems: "center",
        gap: "4px"
    },

    enlargedPdfDocumentContainer: {
        boxSizing: "border-box",
        background: "white",
        maxWidth: "800px",
        margin: "0 auto",
        flex: 1,
        overflow: "auto",
        borderRadius: "4px",
    },

    enlargedPdfDocument: {
        boxSizing: "border-box",
    },

    pagesInput: {
        width: "90px"
    },
});
