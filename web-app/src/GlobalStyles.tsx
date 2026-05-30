import {makeStyles, tokens} from "@fluentui/react-components";

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
        padding: "0 16px",
        boxSizing: "border-box",
        boxShadow: tokens.shadow4,
        position: "fixed",
        zIndex: 2,
        background: tokens.colorNeutralBackground1,
        height: "60px",
        top: 0,
        gap: "8px",
        // Smaller screens: tighten the header and let the action cluster
        // sit closer together so the brand logo and the persona avatar
        // still both fit on a phone.
        "@media (max-width: 768px)": {
            padding: "0 8px",
            gap: "4px",
        },
        "@media (max-width: 480px)": {
            padding: "0 6px",
            gap: "2px",
        },
    },

    mainHeaderAppLogo: {
        flex: 1,
        minWidth: 0,
        overflow: "hidden",
    },

    /**
     * Wrap the FluentUI Persona in this on the main header so that on
     * phone-sized viewports we collapse it down to just the avatar (the
     * user name + email texts are hidden), saving roughly 180-220px of
     * horizontal space.
     *
     * The trigger element is a plain Button (no dropdown chevron) so the
     * collapsed avatar visually matches the other header icon buttons.
     */
    mainHeaderPersona: {
        minWidth: 0,
        // Strip the default text-button padding so the avatar inside
        // sits flush with the button edges (same visual footprint as
        // the surrounding icon-only Buttons in the header).
        paddingLeft: "4px",
        paddingRight: "4px",
        "@media (max-width: 768px)": {
            // Mobile: drop the name + email text and shrink the button
            // to an icon-sized square that lines up with the other
            // header buttons.
            paddingLeft: "2px",
            paddingRight: "2px",
            "& .fui-Persona__primaryText, & .fui-Persona__secondaryText, & .fui-Persona__tertiaryText, & .fui-Persona__quaternaryText": {
                display: "none",
            },
            "& .fui-Persona": {
                gap: 0,
            },
        },
    },

    mainAppSection: {
        width: "100%",
        height: "100%",
        paddingTop: "60px",
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground2,
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