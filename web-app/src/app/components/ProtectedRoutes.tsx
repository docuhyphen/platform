import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import MainMenu from "./MainMenu.tsx";
import AuthBootstrapSplash from "./AuthBootstrapSplash.tsx";
import HelpDocumentationSidebar from "./help-docs/HelpDocumentationSidebar.tsx";
import {HelpSidebarContext} from "../../context/HelpSidebarContext.tsx";
import {useProtectedRoutesStyles} from "./ProtectedRoutesStyles.tsx";

const ProtectedRoute: React.FC<{ element: React.ReactElement, path: string }> = ({element, path}) =>
{
    const styles = useProtectedRoutesStyles();
    const {token, isBootstrapping} = useAuth();
    const [isHelpSidebarOpen, setIsHelpSidebarOpen] = React.useState(false);
    const [requestedArticleId, setRequestedArticleId] = React.useState<string | undefined>();
    const toggleHelpSidebar = React.useCallback(() =>
    {
        setIsHelpSidebarOpen((open) => !open);
    }, []);

    const openHelpArticle = React.useCallback((articleId: string) =>
    {
        setRequestedArticleId(articleId);
        setIsHelpSidebarOpen(true);
    }, []);

    React.useEffect(() =>
    {
        if (!token || isBootstrapping) return;
        const onKeyDown = (event: KeyboardEvent) =>
        {
            if (event.key !== "F1" || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
            event.preventDefault();
            toggleHelpSidebar();
        };
        window.addEventListener("keydown", onKeyDown);
        return () => window.removeEventListener("keydown", onKeyDown);
    }, [isBootstrapping, token, toggleHelpSidebar]);

    // Wait for the cookie-based refresh probe to finish before deciding whether
    // to redirect,  otherwise we flash /sign-in for a frame on cold reopen.
    if (isBootstrapping)
    {
        return <AuthBootstrapSplash/>;
    }

    return token ? (
        <HelpSidebarContext.Provider value={{openHelpArticle}}>
            <div className={styles.appLayout}>
                <div className={styles.appPane}>
                    <MainMenu onToggleHelpSidebar={toggleHelpSidebar}/>
                    {/*<TourCoach/>*/}
                    <div className={styles.pageContent}>{element}</div>
                </div>
                <HelpDocumentationSidebar isOpen={isHelpSidebarOpen}
                                          onOpenChange={setIsHelpSidebarOpen}
                                          requestedArticleId={requestedArticleId}/>
            </div>
        </HelpSidebarContext.Provider>
    ) : (
        <Navigate to={path}/>
    );
};

export default ProtectedRoute;
