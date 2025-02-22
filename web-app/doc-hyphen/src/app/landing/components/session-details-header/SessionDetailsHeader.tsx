import React from 'react';
import {
    Body1,
    Button,
    Caption1,
    Divider,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {bundleIcon, CheckmarkNoteFilled, CheckmarkNoteRegular, DeleteFilled, DeleteRegular, DocumentAddRegular, MoreVerticalRegular, WindowEditFilled, WindowEditRegular} from "@fluentui/react-icons";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useLandingStyles} from "../../LandingStyles.tsx";
import {SharingSessionDetailedDto, SharingSessionStatus} from "../../../models/models.tsx";

interface SessionDetailsHeaderProps {
    sessionDetails: SharingSessionDetailedDto | null;
    setIsDocumentAddDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsSessionEndDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsDeletedSessionDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
}

const SessionDetailsHeader: React.FC<SessionDetailsHeaderProps> = ({
    sessionDetails,
    setIsDocumentAddDialogOpen,
    setIsSessionEndDialogOpen,
    setIsDeletedSessionDialogOpen
}) => {
    const styles = useLandingStyles();
    const DocumentAddIcon = bundleIcon(DocumentAddRegular, DocumentAddRegular);
    const SessionEndIcon = bundleIcon(CheckmarkNoteFilled, CheckmarkNoteRegular);
    const DeleteIcon = bundleIcon(DeleteFilled, DeleteRegular);
    const EditSessionIcon = bundleIcon(WindowEditFilled, WindowEditRegular);

    return (
        <>
            {sessionDetails && (
                <>
                    <div>
                        <Caption1>
                            Started {formatDateTimeWithOrdinal(sessionDetails.createdDate)}
                        </Caption1>
                        {sessionDetails.endDate && (
                            <> | Ended {formatDateTimeWithOrdinal(sessionDetails.createdDate)} </>
                        )}
                        <br/>
                        <Text size={600}>{sessionDetails.sessionName}</Text><br/>
                        <Body1>{sessionDetails.description}</Body1>
                    </div>
                    <div id="sharing-session-actions" className={styles.sharingSessionActions}>
                        <Tooltip content="Add Session Document" relationship="description">
                            <Button icon={<DocumentAddIcon/>}
                                    appearance="primary"
                                    onClick={() => setIsDocumentAddDialogOpen(true)}
                            />
                        </Tooltip>

                        <Menu positioning={{autoSize: true}}>
                            <MenuTrigger disableButtonEnhancement>
                                <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                            </MenuTrigger>
                            <MenuPopover>
                                <MenuList>
                                    <MenuItem icon={<EditSessionIcon/>}
                                              onClick={() => setIsSessionEndDialogOpen(true)}>
                                        Edit
                                    </MenuItem>
                                </MenuList>
                                <Divider/>
                                <MenuList>
                                    <MenuItem icon={<SessionEndIcon/>}
                                              disabled={sessionDetails.status === SharingSessionStatus.ENDED}
                                              onClick={() => setIsSessionEndDialogOpen(true)}>
                                        End
                                    </MenuItem>
                                </MenuList>
                                <MenuList>
                                    <MenuItem icon={<DeleteIcon/>}
                                              onClick={() => setIsDeletedSessionDialogOpen(true)}>
                                        Delete
                                    </MenuItem>
                                </MenuList>
                            </MenuPopover>
                        </Menu>
                    </div>
                </>
            )}
        </>
    );
};

export default SessionDetailsHeader;