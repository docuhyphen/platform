import {makeStyles} from "@fluentui/react-components";

export const useGlobalStyles = makeStyles({
    root: {
        width: "100%",
        height: "100%",
        margin: 0,
        padding: 0,
    },
    fluentProvider: {
        width: "100%",
        height: "100%",
    },
    mainAppHeader: {
        display: "flex",
        width: "100%",
        justifyContent: "space-between",
        alignItems: "center",
        borderBottom: "1px solid rgba(0, 0, 0, .1)",
        padding: "0 16px",
        boxSizing: "border-box",
        boxShadow: "1px 1px 1px 0 rgba(0, 0, 0, 0.1)",
        position: "fixed",
        zIndex: 2,
        background: "white",
        height: "60px",
        top: 0,
        gap: "8px"
    },

    mainHeaderAppLogo: {
        flex: 1
    },

    mainAppSection: {
        width: "100%",
        height: "100%",
        paddingTop: "60px",
        boxSizing: "border-box",
        background: "#f5f5f5",
    },
    mainAppSectionFullHeight: {
        width: "100%",
        height: "100%",
    },

    shadingSessionDocumentCard: {
        marginBottom: "12px",
    },
    shadingSessionDocumentCardHeaderField: {
        flex: 1,
    },
    shadingSessionDocumentCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        boxSizing: "border-box",
        gap: "8px",
    },
    shadingSessionDocumentCardDocType: {
        display: "flex",
        justifyContent: "space-between",
    },
    preLoadingContainer: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
    },
    buttonWithLoading: {
        display: "flex",
        gap: "6px",
    },
    flex1: {
        flex: 1,
    },
    textCenter: {
        textAlign: "center",
    },
});