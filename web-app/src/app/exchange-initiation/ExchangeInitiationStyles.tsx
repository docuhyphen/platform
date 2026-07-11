import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeInitiationStyles = makeStyles({
    sharingDetails: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    sharingDetailsInput: {
        flex: 1,
    },
    exchangeInitiationTaps: {
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
    exchangeDocumentsTabContent: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    exchangeDocumentsRestriction: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
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
            gap: tokens.spacingHorizontalM,
        },
    },
    exchangeDocumentsRestrictionField: {
        flex: 1,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
        // Phones: switch + dropdown stack rather than sit side-by-side,
        // matching the parent's vertical layout.
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: tokens.spacingHorizontalS,
        },
    },
    exchangeDocumentsDropdown: {
        marginRight: tokens.spacingHorizontalXXXL,
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
    shadingExchangeDocumentCard: {
        flex: 1,
    },
    dialogTitle: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    dialogContent: {
        paddingTop: "0"
    },
    dialogContentTransitionFrame: {
        minWidth: 0,
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    dialogContentSlideInFromRight: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(28px)",
            },
            to: {
                opacity: 1,
                transform: "translateX(0)",
            },
        },
        animationDuration: "190ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    dialogContentSlideInFromLeft: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(-28px)",
            },
            to: {
                opacity: 1,
                transform: "translateX(0)",
            },
        },
        animationDuration: "190ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    dialogTitle1: {
        display: "flex",
        justifyContent: "space-between",
    },
    exchangeDetailsTap: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    sharingOptionsTapContent: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    exchangeInitiationSuccess: {
        minHeight: "200px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    exchangeSuccessDetails: {
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalS,
        width: "100%",
        maxWidth: "440px",
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    exchangeSuccessActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
        marginBottom: tokens.spacingVerticalL
    },
    errorMessagesGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },
    iconDeleteFilled: {
        color: tokens.colorPaletteRedForeground1,
    },
    addDocumentButtonContainer: {
        display: "flex",
        justifyContent: "center",
        marginBottom: tokens.spacingVerticalL,
        gap: tokens.spacingHorizontalL,
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
        marginTop: tokens.spacingVerticalXS,
    },
    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        minHeight: "32px",
        cursor: "text",
    },
    variableOverridesPanel: {
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        border: `1px solid ${tokens.colorBrandStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        background: tokens.colorNeutralBackground2,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalM,
    },
    variableOverridesSubtext: {
        color: tokens.colorNeutralForeground3,
    },
    variableOverridesRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
    },
    variableOverridesLabel: {
        minWidth: "120px",
        fontFamily: "monospace",
    },
    variableOverridesInput: {
        flex: 1,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusMedium,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        background: tokens.colorNeutralBackground1,
        color: "inherit",
    },
    saveBlueprintBackRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    blueprintPickerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minHeight: "300px",
    },
    blueprintPickerStickyHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
    },
    blueprintPickerControls: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },
    blueprintPickerSearchRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        width: "100%",
    },
    blueprintPickerSearchField: {
        flex: 1,
    },
    blueprintPickerActiveTagsRow: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
    },
    blueprintPickerFilterPopover: {
        padding: tokens.spacingHorizontalS,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        minWidth: "180px",
        maxWidth: "260px",
    },
    blueprintPickerFilterPopoverList: {
        minHeight: "80px",
        maxHeight: "200px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    blueprintPickerFilterEmptyText: {
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        color: tokens.colorNeutralForeground3,
    },
    blueprintPickerSpinnerWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXL,
    },
    blueprintPickerErrorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    blueprintPickerEmptyText: {
        color: tokens.colorNeutralForeground3,
    },
    blueprintList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        overflowY: "auto",
    },
    blueprintPickerPaginationRow: {
        display: "flex",
        justifyContent: "center",
        paddingTop: tokens.spacingVerticalXS,
    },
    blueprintCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
    },
    blueprintCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    blueprintCardSummary: {
        color: tokens.colorNeutralForeground2,
    },
    blueprintTagRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
    },
    saveBlueprintContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    saveBlueprintErrorText: {
        color: tokens.colorPaletteRedForeground1,
        fontSize: "12px",
    },
    saveBlueprintActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        justifyContent: "flex-end",
        paddingTop: tokens.spacingVerticalXS,
    },
    docLinkedBadgeRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalSNudge,
    },
    choosingBlueprintSubtext: {
        color: tokens.colorNeutralForeground3,
    },
    docPickerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minHeight: "300px",
    },
    docPickerHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
    },
    docPickerSpinnerWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXL,
    },
    docPickerErrorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    docPickerEmptyText: {
        color: tokens.colorNeutralForeground3,
    },
    docPickerList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        overflowY: "auto",
    },
    docPickerEntry: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        cursor: "pointer",
    },
    docPickerEntrySelected: {
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
        backgroundColor: tokens.colorBrandBackground2,
    },
    docPickerEntryHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    docPickerEntryDescription: {
        color: tokens.colorNeutralForeground2,
    },
    docPickerEntryTagRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
    },
});
