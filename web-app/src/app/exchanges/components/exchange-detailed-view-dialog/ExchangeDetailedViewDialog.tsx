import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle
} from "@fluentui/react-components";
import {useExchangeDDetailedViewDialogStyles} from "./ExchangeDetailedViewDialogStyles.tsx";
import ExchangeDetailSection from "./exchange-detail-section/ExchangeDetailSection.tsx";
import ExchangeDetailKeyValueGrid from "./exchange-detail-key-value-grid/ExchangeDetailKeyValueGrid.tsx";
import ExchangeDetailOverviewSection from "./exchange-detail-overview-section/ExchangeDetailOverviewSection.tsx";
import ExchangeDetailParticipantsSection
    from "./exchange-detail-participants-section/ExchangeDetailParticipantsSection.tsx";
import {normalizeExchangeStatus} from "./exchangeDetailedViewFormatting.ts";
import {buildDocumentRows, buildLifecycleRows, buildSharingOptionRows} from "./exchangeDetailedViewRows.ts";

interface ExchangeDetailedViewDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto | null;
}

const ExchangeDetailedViewDialog: React.FC<ExchangeDetailedViewDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange
    }) =>
{
    const styles = useExchangeDDetailedViewDialogStyles();

    if (!exchange) return null;

    const currentStatus = normalizeExchangeStatus(exchange.status, exchange.endDate);

    return <>
        {<Dialog modalType="alert"
                 open={isOpen}>
            <DialogSurface id={"exchange-detailed-view-dialog-dialog-surface-1"}
                           className={styles.dialogSurface}>
                <DialogBody id={"exchange-detailed-view-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-detailed-view-dialog-dialog-title-1"}>Exchange Detailed View</DialogTitle>
                    <DialogContent id={"exchange-detailed-view-dialog-dialog-content-1"}
                                   className={styles.dialogContent}>
                        <ExchangeDetailOverviewSection exchange={exchange}
                                                       currentStatus={currentStatus}/>

                        <ExchangeDetailSection id={"exchange-detailed-view-dialog-div-3"}
                                               titleId={"exchange-detailed-view-dialog-text-4"}
                                               title={"Lifecycle"}>
                            <ExchangeDetailKeyValueGrid id={"exchange-detailed-view-dialog-div-4"}
                                                        rows={buildLifecycleRows(exchange)}/>
                        </ExchangeDetailSection>

                        <ExchangeDetailParticipantsSection exchange={exchange}/>

                        <ExchangeDetailSection id={"exchange-detailed-view-dialog-div-9"}
                                               titleId={"exchange-detailed-view-dialog-text-19"}
                                               title={"Documents"}>
                            <ExchangeDetailKeyValueGrid id={"exchange-detailed-view-dialog-div-10"}
                                                        rows={buildDocumentRows(exchange)}/>
                        </ExchangeDetailSection>

                        <ExchangeDetailSection id={"exchange-detailed-view-dialog-div-11"}
                                               titleId={"exchange-detailed-view-dialog-text-26"}
                                               title={"Sharing options"}>
                            <ExchangeDetailKeyValueGrid id={"exchange-detailed-view-dialog-div-12"}
                                                        rows={buildSharingOptionRows(exchange)}/>
                        </ExchangeDetailSection>
                    </DialogContent>
                    <DialogActions id={"exchange-detailed-view-dialog-dialog-actions-1"}>
                        <Button id={"exchange-detailed-view-close-btn"}
                                appearance="primary"
                                onClick={onDismiss}
                                shape={"circular"}>
                            Close
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default ExchangeDetailedViewDialog;
