import React, {useEffect, useState} from 'react';
import {Document, Page, pdfjs} from 'react-pdf';
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import {useSessionDocumentPreviewerStyles} from "./SessionDocumentPreviewerStyles";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models";
import useToken from "../../../../context/useToken";
import {downloadSharingSessionDocument} from "../../../../services/sharingSessionApi";
import {Button, Input, Text} from "@fluentui/react-components";
import {CollapseIcon, ExpandIcon, LastPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";

pdfjs.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

interface DocumentPreviewerProps
{
    document: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
}

const SessionDocumentPreviewer: React.FC<DocumentPreviewerProps> = ({document: sessionDocument, session}) =>
{
    const [pdfBlob, setPdfBlob] = useState<Blob | null>(null);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);
    const [numPages, setNumPages] = useState<number>(0);
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [isEnlarged, setIsEnlarged] = useState<boolean>(false);
    const token = useToken();

    const styles = useSessionDocumentPreviewerStyles();

    useEffect(() =>
    {
        console.log("Sesstion document changed", sessionDocument.title);
        const fetchDocument = async () =>
        {
            if (sessionDocument && sessionDocument.uploadDate)
            {
                try
                {
                    const response = await downloadSharingSessionDocument(session.id, sessionDocument.id, token);
                    const blob = new Blob([response as Blob], {type: 'application/pdf'});
                    setPdfBlob(blob);

                    // Create Object URL for PDF
                    const url = URL.createObjectURL(blob);
                    setPdfUrl(url);
                }
                catch (error)
                {
                    console.error("Error downloading document:", error);
                }
            }
        };

        fetchDocument();
    }, [sessionDocument]);

    const onDocumentLoadSuccess = ({numPages}: { numPages: number }) =>
    {
        setNumPages(numPages);
    };

    const goToFirstPage = () =>
    {
        setCurrentPage(1);
    };

    const goToLastPage = () =>
    {
        setCurrentPage(numPages);
    };

    const toggleEnlarge = () =>
    {
        setIsEnlarged(prev => !prev);
    };

    const handlePageInputChange = (event: React.ChangeEvent<HTMLInputElement>) =>
    {
        const value = parseInt(event.target.value, 10);
        if (!isNaN(value) && value >= 1 && value <= numPages)
        {
            setCurrentPage(value);
        }
    };

    return (
        <section className={isEnlarged ? styles.enlargedPreviewContainer : styles.previewContainer}>
            <div className={isEnlarged ? styles.enlargedPreviewHeader : styles.previewHeader}>
                {isEnlarged &&
                    <div className={styles.documentName}>
                        <Text size={200}>
                            {session.sessionName}
                        </Text>
                        <Text size={500}>
                            {sessionDocument.title}
                        </Text>
                    </div>
                }
                <div className={isEnlarged ? styles.enlargedPreviewHeaderActions : styles.previewHeaderActions}>


                    {!isEnlarged &&
                        <Button onClick={toggleEnlarge}
                                appearance={"transparent"}
                                icon={isEnlarged ? <CollapseIcon/> : <ExpandIcon/>}>
                        </Button>
                    }
                    <div>
                        <Button onClick={goToFirstPage}
                                icon={<PreviousPageIcon/>}
                                appearance={"transparent"}/>

                        <Input
                            type="text"
                            value={currentPage.toString()}
                            onChange={handlePageInputChange}
                            className={styles.pagesInput}
                            contentAfter={<Text>/{numPages}</Text>}
                        />

                        <Button onClick={goToLastPage}
                                icon={<LastPageIcon/>}
                                appearance={"transparent"}/>

                    </div>

                    {isEnlarged &&
                        <Button onClick={toggleEnlarge}
                                appearance={"transparent"}
                                icon={isEnlarged ? <CollapseIcon/> : <ExpandIcon/>}>
                        </Button>
                    }
                </div>
            </div>
            <div className={isEnlarged ? styles.enlargedPdfDocumentContainer : styles.pdfDocumentContainer}
                 id={"pdfDocumentContainer"}>
                {pdfUrl && (
                    <Document
                        className={isEnlarged ? styles.enlargedPdfDocument : styles.pdfDocument}
                        file={pdfUrl}
                        onLoadSuccess={onDocumentLoadSuccess}
                        onLoadError={(error) => console.error("Failed to load PDF:", error)}
                    >
                        <Page pageNumber={currentPage} scale={isEnlarged ? 1.0 : 1.0}/>
                    </Document>
                )}
            </div>
        </section>
    );
};

export default SessionDocumentPreviewer;
