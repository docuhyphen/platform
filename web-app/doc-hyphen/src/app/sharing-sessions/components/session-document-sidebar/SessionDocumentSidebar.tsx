// web-app/doc-hyphen/src/app/sharing-sessions/components/session-document-sidebar/SessionDocumentSidebar.tsx
import React from "react";
import {
    Button,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    InlineDrawer,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    Text
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {useSessionDocumentSidebarStyles} from "./SessionDocumentSidebarStyles.tsx";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models.tsx";
import {AuditIcon, CommentIcon, DocumentVersionsIcon} from "../../../components/IconBundles.tsx";
import SessionDocumentComments from "./session-document-comments/SessionDocumentComments.tsx";
import SessionDocumentAudit from "./session-document-audit/SessionDocumentAudit.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";

interface SessionDocumentSidebarProps
{
    onOpen: (open: boolean) => void;
    isOpen: boolean;
    sessionDocument: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
}

const SessionDocumentSidebar: React.FC<SessionDocumentSidebarProps> = (
    {
        onOpen,
        isOpen,
        sessionDocument,
        session,
    }) =>
{
    const [selectedValue, setSelectedValue] = React.useState<TabValue>("comments");
    const {appUser} = useAuth()
    const styles = useSessionDocumentSidebarStyles();

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    return (
        <InlineDrawer
            as="aside"
            id={"SessionDocumentSidebar"}
            open={isOpen}
            className={styles.sidebarContainer}
            position="end"
        >
            <DrawerHeader className={styles.drawerHeader}>
                <DrawerHeaderTitle
                    action={
                        <Button
                            size={"small"}
                            appearance="subtle"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                            onClick={() => onOpen(false)}
                        />
                    }>
                    <TabList selectedValue={selectedValue} onTabSelect={onTabSelect}>
                        <Tab id="comments" icon={<CommentIcon/>} value="comments">
                            Comments
                        </Tab>
                        <Tab id="versions" icon={<DocumentVersionsIcon/>} value="versions">
                            Versions
                        </Tab>
                        <Tab id="audit" icon={<AuditIcon/>} value="audit">
                            Audit
                        </Tab>
                    </TabList>
                </DrawerHeaderTitle>
            </DrawerHeader>
            <DrawerBody className={styles.drawerBody}>
                {session && selectedValue === "comments" && (
                    <SessionDocumentComments
                        sessionId={session.id}
                        sessionDocument={sessionDocument}
                        currentUserEmail={appUser?.email}
                    />
                )}
                {selectedValue === "versions" && <div><Text>Document versions will appear here</Text></div>}
                {selectedValue === "audit" && (
                    <SessionDocumentAudit
                        sessionId={session.id}
                        sessionDocument={sessionDocument}
                    />
                )}
            </DrawerBody>
        </InlineDrawer>
    );
};

export default SessionDocumentSidebar;