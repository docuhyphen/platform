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
import {
    bundleIcon, CommentFilled,
    CommentRegular,
    DeleteFilled, DeleteRegular,
    DismissRegular, DocumentBulletListClockFilled,
    DocumentBulletListClockRegular
} from "@fluentui/react-icons";
import {useSessionDocumentSidebarStyles} from "./SessionDocumentSidebarStyles.tsx";
import {DocumentDetailedDto} from "../../../models/models.tsx";

interface SessionDocumentSidebarProps
{
    onOpen: (open: boolean) => void;
    isOpen: boolean;
    sessionDocument: DocumentDetailedDto
}

const SessionDocumentSidebar: React.FC<SessionDocumentSidebarProps> = (
    {
        onOpen,
        isOpen,
        sessionDocument
    }) =>
{
    const [selectedValue, setSelectedValue] = React.useState<TabValue>("comments");
    const styles = useSessionDocumentSidebarStyles();

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    const CommentIcon = bundleIcon(CommentFilled, CommentRegular);
    const AuditIcon = bundleIcon(DocumentBulletListClockFilled, DocumentBulletListClockRegular);

    return (
        <InlineDrawer as="aside"
                      open={isOpen}
                      className={styles.sidebarContainer}
                      position="end">
            <DrawerHeader>
                <DrawerHeaderTitle
                    action={
                        <Button
                            appearance="subtle"
                            aria-label="Close"
                            icon={<DismissRegular/>}
                            onClick={() => onOpen(false)}
                        />
                    }
                >
                    <Text size={400}> {sessionDocument.title} </Text>
                </DrawerHeaderTitle>
            </DrawerHeader>

            <DrawerBody>
                <div>
                    <TabList selectedValue={selectedValue} onTabSelect={onTabSelect}>
                        <Tab id="comments" icon={<CommentIcon/>} value="comments">
                            Comments
                        </Tab>
                        <Tab id="audit" icon={<AuditIcon/>} value="audit">
                            Audit
                        </Tab>
                    </TabList>
                    <div>
                        {selectedValue === "comments" && <div> COMMENTS</div>}
                        {selectedValue === "audit" && <div> AUDIT</div>}
                    </div>
                </div>
            </DrawerBody>
        </InlineDrawer>
    );
};

export default SessionDocumentSidebar;