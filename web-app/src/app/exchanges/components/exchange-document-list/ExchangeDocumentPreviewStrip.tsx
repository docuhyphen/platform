import React from "react";
import {Button, mergeClasses} from "@fluentui/react-components";
import {ChevronLeftRegular, ChevronRightRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import {useDocumentStrip} from "./useDocumentStrip.ts";

interface ExchangeDocumentPreviewStripProps
{
    documents: DocumentDetailedDto[];
    selectedDocumentId?: string;
    renderDocument: (document: DocumentDetailedDto) => React.ReactNode;
}

const ExchangeDocumentPreviewStrip: React.FC<ExchangeDocumentPreviewStripProps> = (props) =>
{
    const styles = useExchangeDocumentsListStyles();
    const {stripRef, canScrollLeft, canScrollRight, scrollByCard, onStripKeyDown} = useDocumentStrip(
        props.selectedDocumentId,
        props.documents.length
    );
    const hasOverflow = canScrollLeft || canScrollRight;

    return (
        <div id="exchange-documents-strip-layout"
                 className={styles.stripLayout}>
                {hasOverflow && (
                    <Button id="exchange-documents-scroll-left"
                            aria-label="Scroll documents left"
                            className={styles.scrollButton}
                            appearance="subtle"
                            shape="circular"
                            disabled={!canScrollLeft}
                            icon={<ChevronLeftRegular/>}
                            onClick={() => scrollByCard(-1)}/>
                )}
                <div id="documents-list-cards"
                     ref={stripRef}
                     className={mergeClasses(styles.cardListSection, !hasOverflow && styles.cardListSectionFullWidth)}
                     tabIndex={0}
                     aria-label="Exchange documents"
                     onKeyDown={onStripKeyDown}>
                    {props.documents.map(props.renderDocument)}
                </div>
                {hasOverflow && (
                    <Button id="exchange-documents-scroll-right"
                            aria-label="Scroll documents right"
                            className={styles.scrollButton}
                            appearance="subtle"
                            shape="circular"
                            disabled={!canScrollRight}
                            icon={<ChevronRightRegular/>}
                            onClick={() => scrollByCard(1)}/>
                )}
        </div>
    );
};

export default ExchangeDocumentPreviewStrip;
