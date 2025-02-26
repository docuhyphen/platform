// SessionDocumentPreviewerStyles.tsx
import { makeStyles } from '@fluentui/react-components';

export const useSessionDocumentPreviewerStyles = makeStyles({

    enlargedPreviewContainer: {
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        background: "white",
        zIndex: 9999,
        overflow: "auto",
        padding: "16px",
        backgroundPosition: "relative"

    },

    previewContainer: {
        display: "flex",
        flexDirection: "row",
        flex: "1",
        background: "white",
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
    },

    enlargedPreviewHeader: {
        border: "10px solid red",
        position: "fixed",
        zIndex: 9998,
    },

    previewHeader: {
        display: "flex",
        flexDirection: "column",
        padding: "8px",
        marginBottom: "8px",
        justifyContent: "center",
        alignItems: "center",
    },

    pdfDocumentContainer: {
        flex: "1",
        background: "white",
        border: "1px solid rgba(0, 0, 0, .1)",
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
    },

    pagesInput: {
        width: "90px"
    }
});
