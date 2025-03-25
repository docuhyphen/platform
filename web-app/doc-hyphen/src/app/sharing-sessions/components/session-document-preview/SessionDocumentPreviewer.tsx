import React, {useEffect, useRef, useState} from 'react';
import {Document, Page, pdfjs} from 'react-pdf';
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import {Button, Input, Text} from "@fluentui/react-components";
import {downloadPreviewPDFSharingSessionDocument} from "../../../../services/sharingSessionApi";
import {CollapseIcon, ExpandIcon, LastPageIcon, PreviousPageIcon} from "../../../components/IconBundles.tsx";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models";
import {useSessionDocumentPreviewerStyles} from "./SessionDocumentPreviewerStyles";
import useToken from "../../../../context/useToken";

pdfjs.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

interface DocumentPreviewerProps
{
    document: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
}

const SessionDocumentPreviewer: React.FC<DocumentPreviewerProps> = (
    {
        document: sessionDocument,
        session
    }) =>
{
    const styles = useSessionDocumentPreviewerStyles();
    const pdfContainerRef = useRef<HTMLDivElement>(null);
    const token = useToken();
    const [pdfBlob, setPdfBlob] = useState<Blob | null>(null);
    const [pdfUrl, setPdfUrl] = useState<string | null>(null);
    const [numPages, setNumPages] = useState<number>(0);
    const [currentPage, setCurrentPage] = useState<number>(1);
    const [isEnlarged, setIsEnlarged] = useState<boolean>(false);

    useEffect(() =>
    {
        const fetchDocument = async () =>
        {
            if (sessionDocument?.uploadDate)
            {
                try
                {
                    const response = await downloadPreviewPDFSharingSessionDocument(session.id, sessionDocument.id);
                    const blob = new Blob([response as Blob], {type: 'application/pdf'});
                    setPdfBlob(blob);
                    const url = URL.createObjectURL(blob);
                    setPdfUrl(url)
                }
                catch (error)
                {
                    console.error("Error downloading document:", error);
                }
            }
        };
        fetchDocument();
        setCurrentPage(1);
    }, [sessionDocument]);

    const onDocumentLoadSuccess = ({numPages}: { numPages: number }) =>
    {
        setNumPages(numPages);
        setCurrentPage(1);
    };

    const goToPage = (pageNumber: number) =>
    {
        if (pageNumber >= 1 && pageNumber <= numPages)
        {
            const pageElement = pdfContainerRef.current?.querySelector(`[data-page-number="\${pageNumber}"]`);
            if (pageElement)
            {
                pageElement.scrollIntoView({behavior: 'smooth'});
            }
            setCurrentPage(pageNumber);
        }
    };

    const handlePageInputChange = (event: React.ChangeEvent<HTMLInputElement>) =>
    {
        const parsed = parseInt(event.target.value, 10);
        goToPage(parsed);
    };

    const handleNextPage = () =>
    {
        goToPage(currentPage + 1);
    };

    const handlePreviousPage = () =>
    {
        goToPage(currentPage - 1);
    };

    const toggleEnlarge = () =>
    {
        setIsEnlarged(prev => !prev);
    };

    return (
        <section className={isEnlarged ? styles.enlargedPreviewContainer : styles.previewContainer}
                 id={"SessionDocumentSidebar"}>
            <div className={isEnlarged ? styles.enlargedPreviewHeader : styles.previewHeader}>
                {isEnlarged && (
                    <div className={styles.documentName}>
                        <Text size={200}>{session.sessionName}</Text>
                        <Text size={500}>{sessionDocument.title}</Text>
                    </div>
                )}
                <div className={isEnlarged ? styles.enlargedPreviewHeaderActions : styles.previewHeaderActions}>
                    {!isEnlarged && (
                        <Button onClick={toggleEnlarge} appearance="transparent"
                                icon={isEnlarged ? <CollapseIcon/> : <ExpandIcon/>}/>
                    )}

                    <div>
                        <Button onClick={() => goToPage(1)} icon={<PreviousPageIcon/>} appearance="transparent"/>
                        <Button onClick={handlePreviousPage} appearance="transparent">Prev</Button>

                        <Input
                            type="text"
                            value={currentPage.toString()}
                            onChange={handlePageInputChange}
                            className={styles.pagesInput}
                            contentAfter={<Text>/{numPages}</Text>}
                        />

                        <Button onClick={handleNextPage} appearance="transparent">Next</Button>
                        <Button onClick={() => goToPage(numPages)} icon={<LastPageIcon/>} appearance="transparent"/>
                    </div>

                    {isEnlarged && (
                        <Button onClick={toggleEnlarge} appearance="transparent"
                                icon={isEnlarged ? <CollapseIcon/> : <ExpandIcon/>}/>
                    )}
                </div>
            </div>

            <div className={isEnlarged ? styles.enlargedPdfDocumentContainer : styles.pdfDocumentContainer}
                 id="pdfDocumentContainer" ref={pdfContainerRef}>
                {pdfUrl && (
                    <Document
                        className={isEnlarged ? styles.enlargedPdfDocument : styles.pdfDocument}
                        file={pdfUrl}
                        onLoadSuccess={onDocumentLoadSuccess}
                        onLoadError={(error) => console.error("Failed to load PDF:", error)}
                    >
                        {numPages > 0 && (
                            <Page
                                key={`page_\${currentPage}`}
                                pageNumber={currentPage}
                                scale={isEnlarged ? 1.1 : 1.0}
                            />
                        )}
                    </Document>
                )}
            </div>
        </section>
    );
};

export default SessionDocumentPreviewer;