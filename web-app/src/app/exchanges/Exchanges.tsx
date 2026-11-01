import React from 'react';
import {Toaster, useId, useToastController} from "@fluentui/react-components";
import ExchangePreLoader from "./components/exchange-pre-loader/ExchangePreLoader.tsx";
import ExchangesSection from "./components/exchanges-section/ExchangesSection.tsx";
import ExchangesEmptyWorkspace from "./components/exchanges-empty-workspace/ExchangesEmptyWorkspace.tsx";
import {useExchangesWorkspace} from "./useExchangesWorkspace.ts";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";

const Exchanges: React.FC = () =>
{
    const toasterId = useId("exchanges-toaster");
    const {dispatchToast} = useToastController(toasterId);
    const workspace = useExchangesWorkspace(dispatchToast);
    const {preparingExchanges, appUserHasExchanges} = workspace;

    return (
        <>
            <Toaster id={"exchanges-toaster"}
                     toasterId={toasterId}
                     position="bottom-end"/>
            {preparingExchanges && <ExchangePreLoader/>}
            {!preparingExchanges && appUserHasExchanges && <ExchangesSection workspace={workspace}/>}
            {!preparingExchanges && !appUserHasExchanges && <ExchangesEmptyWorkspace/>}
        </>
    );
};

export default Exchanges;
