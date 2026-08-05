import React from "react";
import {Button} from "@fluentui/react-components";
import {ChevronLeftRegular, ChevronRightRegular, GridRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import {useDocumentStrip} from "./useDocumentStrip.ts";

interface ExchangeDocumentPreviewStripProps
{
    documents: DocumentDetailedDto[];
    selectedDocumentId?: string;
    onShowGrid: () => void;
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
                <div id="exchange-documents-left-controls"
                     className={styles.leftControls}>
                    {hasOverflow && (
                        <Button id="exchange-documents-scroll-left"
                                aria-label="Scroll documents left"
                                className={styles.leftNavigationButton}
                                appearance="subtle"
                                shape="circular"
                                disabled={!canScrollLeft}
                                icon={<ChevronLeftRegular/>}
                                onClick={() => scrollByCard(-1)}/>
                    )}
                    <Button id="exchange-documents-show-grid"
                            aria-label="Show documents in grid"
                            className={styles.gridViewButton}
                            appearance="subtle"
                            shape="circular"
                            icon={<GridRegular/>}
                            onClick={props.onShowGrid}/>
                </div>
                <div id="documents-list-cards"
                     ref={stripRef}
                     className={styles.cardListSection}
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
