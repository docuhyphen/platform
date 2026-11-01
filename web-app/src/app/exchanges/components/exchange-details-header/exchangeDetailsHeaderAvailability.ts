import {ExchangeStatus} from "../../../models/models.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";

export const isExchangeClosed = (status?: ExchangeStatus): boolean =>
    status === ExchangeStatus.ENDED || status === ExchangeStatus.RESCINDED;

export const canAddDocument = (status: ExchangeStatus | undefined, permissions: ExchangePermissions): boolean =>
    !isExchangeClosed(status) && permissions.canAddExchangeDocument;

export const canEditSharingOptions = (status: ExchangeStatus | undefined, permissions: ExchangePermissions): boolean =>
    !isExchangeClosed(status) && permissions.canEditSharingOptions;

export const canEndExchange = (status: ExchangeStatus | undefined, permissions: ExchangePermissions): boolean =>
    !isExchangeClosed(status) && permissions.canEndExchange;

export const canRescindExchange = (status: ExchangeStatus | undefined, permissions: ExchangePermissions): boolean =>
    (status === ExchangeStatus.INITIATED || status === ExchangeStatus.ACCEPTED_STARTED) && permissions.canDeleteExchange;

export const canRecreateRejectedExchange = (status: ExchangeStatus | undefined, permissions: ExchangePermissions): boolean =>
    status === ExchangeStatus.REJECTED && permissions.canDeleteExchange;
