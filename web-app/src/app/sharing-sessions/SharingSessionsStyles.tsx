import {makeStyles, tokens} from "@fluentui/react-components";

export const useSharingSessionsStyles = makeStyles({

    container: {
        display: "flex",
        gap: "16px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box"
    },
    containerNoSessions: {
        display: "flex",
        gap: "20px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        maxWidth: "640px",
        margin: "auto",
        textAlign: "center"
    },
    noSessionsIllustration: {
        width: "100%",
        maxWidth: "440px",
        height: "auto",
        color: "var(--colorBrandForeground1)",
        marginBottom: "8px"
    },
    sharingSessionDocumentsContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        flex: 1
    },

    sharingSessionDocumentsDetails: {
        width: "100%",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    sharingSessionDocumentsDetailsList: {
        width: "100%",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    detailsContainer: {
        position: "relative",
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "480px",
        minHeight: 0,
    },

    detailsContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        transition: "opacity 180ms ease",
        opacity: 1,
        flex: 1,
        minHeight: 0,
    },

    detailsContentLoading: {
        opacity: 0.56,
    },

    detailsLoadingOverlay: {
        position: "absolute",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        pointerEvents: "none",
    },

    noSessionSelectedSection: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    },
    inboxEmptyDetailsContent: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        alignItems: "center",
        maxWidth: "520px",
        textAlign: "center",
        padding: "16px"
    },
    inboxEmptyIllustration: {
        width: "100%",
        maxWidth: "340px",
        height: "auto",
        color: tokens.colorBrandForeground1
    },
    inboxEmptyActions: {
        display: "flex",
        gap: "8px",
        justifyContent: "center",
        flexWrap: "wrap"
    },
    documentsSectionContainer: {
        display: "flex",
        flex: 1,
        minHeight: 0,
        height: "auto",
        overflow: "hidden",
    },
    documentsSection: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        flex: "1",
        minWidth: "200px",
        minHeight: 0,
        overflow: "hidden",
    },
    noSessionImg: {
        width: "300px"
    },
});