import React from "react";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";

interface ExchangeDocumentBrowseViewProps
{
    documents: DocumentDetailedDto[];
    renderDocument: (document: DocumentDetailedDto) => React.ReactNode;
}

const ExchangeDocumentBrowseView: React.FC<ExchangeDocumentBrowseViewProps> = (props) =>
{
    const styles = useExchangeDocumentsListStyles();

    return (
        <div id="exchange-documents-browse-layout"
             className={styles.browseLayout}>
            <div id="documents-list-cards"
                 className={styles.browseCardGrid}
                 aria-label="Exchange documents">
                {props.documents.map(props.renderDocument)}
            </div>
        </div>
    );
};

export default ExchangeDocumentBrowseView;
