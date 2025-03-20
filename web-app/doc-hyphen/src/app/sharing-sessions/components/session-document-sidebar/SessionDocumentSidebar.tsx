import React from "react";
import {
    Button,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    Field,
    InlineDrawer,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    Text,
    Textarea
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {useSessionDocumentSidebarStyles} from "./SessionDocumentSidebarStyles.tsx";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {AuditIcon, CommentIcon, DocumentVersionsIcon, SendCommentIcon} from "../../../components/IconBundles.tsx";

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

    return (
        <InlineDrawer as="aside"
                      id={"SessionDocumentSidebar"}
                      open={isOpen}
                      className={styles.sidebarContainer}
                      position="end">
            <DrawerHeader className={styles.drawerHeader}>
                <DrawerHeaderTitle
                    action={
                        <Button
                            size={"small"}
                            appearance="primary"
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
                {selectedValue === "comments" && (<>

                        {Array.from({length: 80}, (_, index) => (
                            <div><Text size={400} key={index}>Example comment {index + 1}</Text></div>
                        ))}
                        <div className={styles.commentFieldContainer}>
                            <Field className={styles.commentField}>
                                <Textarea placeholder="Add a comment"
                                          maxLength={255}/>
                            </Field>
                            <Button icon={<SendCommentIcon/>}
                                    appearance={"transparent"}/>
                        </div>
                    </>
                )}
                {selectedValue === "versions" && <div> VERSIONS</div>}
                {selectedValue === "audit" && <div> AUDIT</div>}
            </DrawerBody>
        </InlineDrawer>
    );
};

export default SessionDocumentSidebar;