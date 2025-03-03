import React from "react";
import {useNoAuthSessionDocumentListStyles} from "./NoAuthSessionDocumentListStyles.tsx";
import {SharingSessionDetailedDto} from "../../../models/models.tsx";
import {Body1, Button, Caption1, Card, CardHeader} from "@fluentui/react-components";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {DocumentAddIcon} from "../../../components/IconBundles.tsx";

interface NoAuthSessionDocumentListProps
{

    session: SharingSessionDetailedDto;
}

const NoAuthSessionDocumentList: React.FC<NoAuthSessionDocumentListProps> = (
    {
        session
    }) =>
{
    const styles = useNoAuthSessionDocumentListStyles();

    const onUploadDocument = () =>
    {
        alert('Upload document');
    }

    const renderDocumentCard = (sessionDocument) =>
    {
        return (
            <Card key={sessionDocument.id}>
                <CardHeader
                    header={<Body1>
                        <b>{sessionDocument.title}</b>
                    </Body1>}
                    description={
                        <>
                            {sessionDocument.uploadDate ? (
                                <Caption1>
                                    Uploaded {formatDateTimeWithOrdinal(sessionDocument.uploadDate)}
                                </Caption1>
                            ) : (
                                <Button appearance="transparent"
                                        icon={<DocumentAddIcon/>}
                                        onClick={onUploadDocument}>
                                    Upload new document
                                </Button>
                            )}
                        </>
                    }
                />
            </Card>
        );
    }

    return (
        <section className={styles.container}>
            {session?.documents?.map((document, _) =>
                renderDocumentCard(document)
            )}
        </section>
    );
}

export default NoAuthSessionDocumentList;