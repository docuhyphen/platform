import React from 'react';
import {Navigate} from 'react-router-dom';
import {makeStyles} from "@fluentui/react-components";
import {useAuth} from '../../context/AuthContext';
import MainMenu from "./MainMenu.tsx";
import AuthBootstrapSplash from "./AuthBootstrapSplash.tsx";
import HelpDocumentationSidebar from "./help-docs/HelpDocumentationSidebar.tsx";

const useStyles = makeStyles({
    appLayout: {
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "row",
        overflow: "hidden",
    },
    appPane: {
        flex: 1,
        minWidth: 0,
        height: "100%",
        position: "relative",
        transform: "translateZ(0)",
    },
    pageContent: {
        height: "100%",
    },
});

const ProtectedRoute: React.FC<{ element: React.ReactElement, path: string }> = ({element, path}) =>
{
    const styles = useStyles();
    const {token, isBootstrapping} = useAuth();
    const [isHelpSidebarOpen, setIsHelpSidebarOpen] = React.useState(false);

    // Wait for the cookie-based refresh probe to finish before deciding whether
    // to redirect,  otherwise we flash /sign-in for a frame on cold reopen.
    if (isBootstrapping)
    {
        return <AuthBootstrapSplash/>;
    }

    return token ? (
        <div className={styles.appLayout}>
            <div className={styles.appPane}>
                <MainMenu onToggleHelpSidebar={() => setIsHelpSidebarOpen((open) => !open)}/>
                {/*<TourCoach/>*/}
                <div className={styles.pageContent}>{element}</div>
            </div>
            <HelpDocumentationSidebar isOpen={isHelpSidebarOpen}
                                      onOpenChange={setIsHelpSidebarOpen}/>
        </div>
    ) : (
        <Navigate to={path}/>
    );
};

export default ProtectedRoute;