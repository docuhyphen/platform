import React from "react";
import {Portal} from "@fluentui/react-components";
import {ReOrderDotsVertical20Regular} from "@fluentui/react-icons";
import {useHelpDocumentationSidebarStyles} from "./HelpDocumentationSidebarStyles.tsx";
import {useHelpPanelResize} from "./useHelpPanelResize.ts";
import {useHelpDocumentationNavigation} from "./useHelpDocumentationNavigation.ts";
import HelpDocumentationHeader from "./help-documentation-header/HelpDocumentationHeader.tsx";
import HelpDocumentationNav from "./help-documentation-nav/HelpDocumentationNav.tsx";

type HelpDocumentationSidebarProps = {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    requestedArticleId?: string;
};

const HelpDocumentationSidebar: React.FC<HelpDocumentationSidebarProps> = ({isOpen, onOpenChange, requestedArticleId}) =>
{
    const styles = useHelpDocumentationSidebarStyles();
    const {
        sections,
        activeArticle,
        activeArticleId,
        showNav,
        openSection,
        setOpenSection,
        searchQuery,
        setSearchQuery,
        searchResults,
        handleNavToggle,
        selectArticle,
        onContentClick,
    } = useHelpDocumentationNavigation(requestedArticleId);
    const {spacerRef, panelRef, onResizePointerDown} = useHelpPanelResize();

    if (!isOpen) return null;

    return (
        <>
            <div
                id={"help-documentation-sidebar-div-1"}
                ref={spacerRef}
                className={styles.spacer}
                aria-hidden
            />

            <Portal>
                <aside id={"help-documentation-sidebar-aside-1"}
                       ref={panelRef}
                       className={styles.panel}
                       role="dialog"
                       aria-label="Help documentation">

                    <div id={"help-documentation-sidebar-div-2"}
                         className={styles.resizeHandle}
                         onPointerDown={onResizePointerDown}
                         aria-label="Drag to resize help panel"
                         title="Drag to resize">
                        <span id={"help-documentation-sidebar-span-1"}
                              className={styles.resizeGrip}
                              aria-hidden>
                            <ReOrderDotsVertical20Regular/>
                        </span>
                    </div>

                    <HelpDocumentationHeader activeArticle={activeArticle}
                                             showNav={showNav}
                                             onNavToggle={handleNavToggle}
                                             onClose={() => onOpenChange(false)}/>

                    {showNav ? (
                        <HelpDocumentationNav sections={sections}
                                              activeArticleId={activeArticleId}
                                              openSection={openSection}
                                              onOpenSectionChange={setOpenSection}
                                              searchQuery={searchQuery}
                                              onSearchQueryChange={setSearchQuery}
                                              searchResults={searchResults}
                                              onSelectArticle={selectArticle}/>
                    ) : (
                        <div id={"help-documentation-sidebar-div-11"}
                             className={styles.content}
                             onClick={onContentClick}>
                            {activeArticle.content}
                        </div>
                    )}
                </aside>
            </Portal>
        </>
    );
};

export default HelpDocumentationSidebar;
