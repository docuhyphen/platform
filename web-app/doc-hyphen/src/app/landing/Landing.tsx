import React, {useEffect, useState} from 'react';
import SharingSessionList from "../sharing-session-list/SharingSessionList.tsx";
import {fetchSignedInUserAppUserSharingSession} from "../../services/api.ts";
import useToken from "../../context/useToken.tsx";
import "./Landing.css";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {
    Body1,
    Button,
    Caption1,
    Card,
    CardHeader,
    Divider,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Skeleton,
    SkeletonItem,
    Subtitle2,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {
    ArrowDownloadRegular,
    ArrowUploadRegular,
    CheckmarkNoteRegular,
    CommentNoteRegular,
    DeleteRegular,
    DocumentAddRegular,
    DocumentBulletListClockRegular,
    DocumentPrintRegular,
    InfoRegular,
    MoreVerticalRegular, NotepadEditRegular
} from "@fluentui/react-icons";
import {formatDate} from "../helpers.ts";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../models/models.tsx";

const useSessionDetails = (selectedSessionId: string | null, token: string | null) =>
{
    const [sessionDetails, setSessionDetails] = useState<SharingSessionDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(false);

    useEffect(() =>
    {
        if (selectedSessionId)
        {
            const fetchDetails = async () =>
            {
                setFetchingDetails(true);
                setSessionDetails(null);

                try
                {
                    const details = await fetchSignedInUserAppUserSharingSession(selectedSessionId, token);
                    setSessionDetails(details as SharingSessionDetailedDto);
                }
                catch (error)
                {
                    console.error(error);
                }
                finally
                {
                    setFetchingDetails(false);
                }
            };
            fetchDetails();
        }
    }, [selectedSessionId, token]);

    return {sessionDetails, fetchingDetails};
};

const Landing: React.FC = () =>
{
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const token = useToken();
    const {sessionDetails, fetchingDetails} = useSessionDetails(selectedSessionId, token);

    useEffect(() =>
    {
        const randomDelay = Math.floor(Math.random() * 5000) + 1000;
        setTimeout(() =>
        {
            setIsLoading(false);
        }, randomDelay);
    }, []);

    return (
        isLoading ? <PreLanding/> :
            <section id="sharing-sessions-container">
                <div>
                    <SharingSessionList onSelectionChange={setSelectedSessionId}/>
                </div>
                <div id="sharing-session-details-container">
                    <div id="sharing-session-head-container">
                        {fetchingDetails ? (
                            <Skeleton>
                                <SkeletonItem/>
                                <SkeletonItem size={8}/>
                            </Skeleton>
                        ) : (
                            sessionDetails && (
                                <>
                                    <div>
                                        <Caption1>
                                            Started {formatDate(sessionDetails.createdDate)}</Caption1>
                                        {
                                            sessionDetails.endDate &&
                                            <> | Ended {formatDate(sessionDetails.createdDate)} </>
                                        }
                                        <br/>
                                        <Text size={600}>{sessionDetails.sessionName}</Text><br/>
                                        <Body1>{sessionDetails.description}</Body1>
                                    </div>
                                    <div id="sharing-session-actions">
                                        <Tooltip content="Session Comments" relationship="description">
                                            <Button icon={<CommentNoteRegular/>} appearance="subtle"/>
                                        </Tooltip>
                                        <Tooltip content="Session Audit" relationship="description">
                                            <Button icon={<DocumentBulletListClockRegular/>} appearance="subtle"/>
                                        </Tooltip>
                                        <Tooltip content="Add Session Document" relationship="description">
                                            <Button icon={<DocumentAddRegular/>} appearance="primary"/>
                                        </Tooltip>

                                        <Menu positioning={{autoSize: true}}>
                                            <MenuTrigger disableButtonEnhancement>
                                                <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                                            </MenuTrigger>
                                            <MenuPopover>
                                                <MenuList>
                                                    <MenuItem icon={<CheckmarkNoteRegular/>}>End Session</MenuItem>
                                                </MenuList>
                                            </MenuPopover>
                                        </Menu>
                                    </div>
                                </>
                            )
                        )}
                    </div>
                    {sessionDetails && (
                        <div>
                            <p>
                                <Subtitle2>Session Documents</Subtitle2>
                            </p>
                            <div id={"documents-card-list"}>
                                {sessionDetails.documents?.map((document: DocumentDetailedDto) => (
                                    <Card key={document.id}>
                                        <CardHeader
                                            header={<Body1><b>{document.title}</b></Body1>}
                                            description={
                                                <>
                                                    {document.uploadDate ? (
                                                        <Caption1>Uploaded {formatDate(document.uploadDate)}</Caption1>
                                                    ) : (
                                                        <Button appearance="transparent" icon={<DocumentAddRegular/>}>
                                                            Upload new document
                                                        </Button>
                                                    )}
                                                </>
                                            }
                                            action={
                                                <div>
                                                    <Menu positioning={{autoSize: true}}>
                                                        <MenuTrigger disableButtonEnhancement>
                                                            <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                                                        </MenuTrigger>
                                                        <MenuPopover>
                                                            <MenuList>
                                                                <MenuItem icon={<NotepadEditRegular/>}>Edit</MenuItem>
                                                                <Divider/>
                                                                <MenuItem icon={<ArrowUploadRegular/>}>Upload</MenuItem>
                                                                <MenuItem
                                                                    icon={<ArrowDownloadRegular/>}>Download</MenuItem>
                                                                <MenuItem
                                                                    icon={<DocumentPrintRegular/>}>Print</MenuItem>
                                                                <MenuItem icon={<DeleteRegular/>}>Delete</MenuItem>
                                                                <Divider/>
                                                                <MenuItem icon={<InfoRegular/>}>More info</MenuItem>
                                                            </MenuList>
                                                        </MenuPopover>
                                                    </Menu>
                                                </div>
                                            }
                                        />
                                    </Card>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </section>
    );
};

export default Landing;