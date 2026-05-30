import {makeStyles, tokens} from "@fluentui/react-components";

export const useSharingSessionInitiationStyles = makeStyles({
    sharingDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    sharingDetailsInput: {
        flex: 1,
    },
    sharingSessionInitiationTaps: {
        minHeight: "400px",
        // Phones: the dialog shrinks to viewport width via the global
        // .fui-DialogSurface override in index.css. The tab content has
        // its own min-height that's fine, but we relax the min-width and
        // allow the inner panels to wrap to avoid horizontal overflow.
        "@media (max-width: 768px)": {
            minHeight: "auto",
            width: "100%",
            minWidth: 0,
        },
    },
    sharingSessionDocumentsTabContent: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    sharingSessionDocumentsRestriction: {
        display: "flex",
        gap: "8px",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        // Phones: stack the Restrict-type switch, the type Dropdown,
        // and the Required checkbox vertically so each control gets
        // the full row width instead of being squeezed off-screen.
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: "12px",
        },
    },
    sharingSessionDocumentsRestrictionField: {
        flex: 1,
        display: "flex",
        alignItems: "center",
        gap: "8px",
        minWidth: 0,
        // Phones: switch + dropdown stack rather than sit side-by-side,
        // matching the parent's vertical layout.
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: "8px",
        },
    },
    sharingSessionDocumentsDropdown: {
        marginRight: "36px",
        minWidth: "100px",
        // Phones: take full available width and drop the right margin
        // (which was there to visually separate it from the sibling
        // Required checkbox in the horizontal layout).
        "@media (max-width: 768px)": {
            marginRight: 0,
            width: "100%",
            minWidth: 0,
        },
    },
    shadingSessionDocumentCard: {
        flex: 1,
    },
    dialogTitle: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    dialogTitle1: {
        display: "flex",
        justifyContent: "space-between",
    },
    sessionDetailsTap: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    sharingOptionsTapContent: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    sharingSessionInitiationSuccess: {
        minHeight: "200px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: "16px",
    },
    sharingSessionSuccessDetails: {
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        columnGap: "12px",
        rowGap: "8px",
        width: "100%",
        maxWidth: "440px",
        padding: "12px",
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    sharingSessionSuccessActions: {
        display: "flex",
        gap: "8px",
        alignItems: "center",
    },
    errorMessagesGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    iconDeleteFilled: {
        color: tokens.colorPaletteRedForeground1,
    },
    addDocumentButtonContainer: {
        display: "flex",
        justifyContent: "center",
    },
    dialogContentContainer: {
        minHeight: "460px",
        // Phones: don't force a 460px min-height (some phones in
        // landscape barely have that vertical room) and never allow the
        // form to push the dialog wider than the viewport.
        "@media (max-width: 768px)": {
            minHeight: "auto",
            minWidth: 0,
            width: "100%",
        },
    },

    /**
     * Mobile-only: the selected tab's full label rendered above the tab
     * content. On phone viewports the tabs themselves collapse to
     * icon-only buttons; this title keeps the user oriented.
     */
    mobileSelectedTabTitle: {
        display: "block",
        marginTop: "4px",
    },
});