import React from 'react';
import {Button, Tooltip} from "@fluentui/react-components";
import {ToggleHeaderDownIcon, ToggleHeaderUpIcon} from "../../../../components/IconBundles.tsx";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {useMediaQuery} from "../../../../../utils/useMediaQuery.ts";
import {ExchangeDetailsHeaderActionHandlers} from "../exchangeDetailsHeaderActionHandlers.ts";
import ExchangeDetailsHeaderInlineActions
    from "../exchange-details-header-inline-actions/ExchangeDetailsHeaderInlineActions.tsx";
import ExchangeDetailsHeaderMenu from "../exchange-details-header-menu/ExchangeDetailsHeaderMenu.tsx";
import {useExchangeDetailsHeaderActionsStyles} from "./ExchangeDetailsHeaderActionsStyles.tsx";

interface ExchangeDetailsHeaderActionsProps extends ExchangeDetailsHeaderActionHandlers
{
    exchangeDetails: ExchangeDetailedDto;
    isExpanded: boolean;
    detailsToggleTooltip: string;
    onToggleDetails: () => void;
}

const ExchangeDetailsHeaderActions: React.FC<ExchangeDetailsHeaderActionsProps> = (props) =>
{
    const {exchangeDetails, isExpanded, detailsToggleTooltip, onToggleDetails, ...handlers} = props;
    const styles = useExchangeDetailsHeaderActionsStyles();
    const collapseHeaderActionsToMenu = useMediaQuery('(max-width: 1024px)');

    return (
        <div id={"exchange-details-header-div-7"}
             className={styles.actions}>
            {!collapseHeaderActionsToMenu && (
                <ExchangeDetailsHeaderInlineActions exchangeDetails={exchangeDetails}
                                                    {...handlers}/>
            )}
            <ExchangeDetailsHeaderMenu exchangeDetails={exchangeDetails}
                                       showCollapsedActions={collapseHeaderActionsToMenu}
                                       {...handlers}/>

            <Tooltip content={detailsToggleTooltip}
                     relationship="description">
                <Button id="exchange-details-header-toggle-details"
                        onClick={onToggleDetails}
                        size={"small"}
                        appearance={"subtle"}
                        shape={"circular"}
                        aria-label={detailsToggleTooltip}
                        icon={isExpanded ? <ToggleHeaderUpIcon/> : <ToggleHeaderDownIcon/>}/>
            </Tooltip>
        </div>
    );
};

export default ExchangeDetailsHeaderActions;
