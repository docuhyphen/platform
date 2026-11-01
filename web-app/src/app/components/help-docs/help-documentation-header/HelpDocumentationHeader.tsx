import React from "react";
import {Button, Text} from "@fluentui/react-components";
import {ArrowLeftRegular, DismissFilled, Navigation24Regular} from "@fluentui/react-icons";
import type {HelpDocArticle} from "../helpDocTypes.ts";
import {useHelpDocumentationHeaderStyles} from "./HelpDocumentationHeaderStyles.tsx";

type HelpDocumentationHeaderProps = {
    activeArticle: HelpDocArticle;
    showNav: boolean;
    onNavToggle: () => void;
    onClose: () => void;
};

const HelpDocumentationHeader: React.FC<HelpDocumentationHeaderProps> = ({activeArticle, showNav, onNavToggle, onClose}) =>
{
    const styles = useHelpDocumentationHeaderStyles();

    return (
        <div id={"help-documentation-sidebar-div-3"}
             className={styles.header}>
            <div id={"help-documentation-sidebar-div-4"}
                 className={styles.breadcrumbRow}>
                <Text id={"help-documentation-sidebar-text-1"}
                      size={200}>Help</Text>
                <Text id={"help-documentation-sidebar-text-2"}
                      size={200}
                      className={styles.crumbSeparator}>/</Text>
                <Text id={"help-documentation-sidebar-text-3"}
                      size={200}>{activeArticle.sectionTitle}</Text>
                {!showNav && (
                    <>
                        <Text id={"help-documentation-sidebar-text-4"}
                              size={200}
                              className={styles.crumbSeparator}>/</Text>
                        <Text id={"help-documentation-sidebar-text-5"}
                              size={200}>{activeArticle.title}</Text>
                    </>
                )}
            </div>

            <div id={"help-documentation-sidebar-div-5"}
                 className={styles.titleRow}>
                <Button
                    id={"help-nav-toggle-btn"}
                    appearance="subtle"
                    shape="circular"
                    icon={showNav ? <ArrowLeftRegular/> : <Navigation24Regular/>}
                    aria-label={showNav ? "Back to article" : "Open documentation menu"}
                    onClick={onNavToggle}
                />
                <Text id={"help-documentation-sidebar-text-6"}
                      className={styles.titleText}>
                    {showNav ? "Contents" : activeArticle.title}
                </Text>
                <div id={"help-documentation-sidebar-div-6"}
                     className={styles.headerActions}>
                    <Button
                        id={"help-close-btn"}
                        appearance="subtle"
                        shape="circular"
                        icon={<DismissFilled/>}
                        aria-label="Close help panel"
                        onClick={onClose}
                    />
                </div>
            </div>
        </div>
    );
};

export default HelpDocumentationHeader;
