import React from "react";
import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {usePlanFeature} from "../../../hooks/subscription/usePlanFeature.ts";
import {PlanFeature} from "../../models/models.tsx";

interface InformationRequestFeatureGateProps
{
    idPrefix: string;
    children: React.ReactNode;
}

const InformationRequestFeatureGate = ({idPrefix, children}: InformationRequestFeatureGateProps) =>
{
    const feature = usePlanFeature(PlanFeature.INFORMATION_REQUESTS);

    if (!feature.isKnown)
    {
        return (
            <Spinner id={`${idPrefix}-plan-loading`}
                     size={"medium"}
                     label={"Checking your plan"}/>
        );
    }
    if (!feature.isAvailable)
    {
        return (
            <MessageBar id={`${idPrefix}-unavailable`}
                        intent={"warning"}>
                <MessageBarBody>Information Requests are not included in your plan.</MessageBarBody>
            </MessageBar>
        );
    }
    return <>{children}</>;
};

export default InformationRequestFeatureGate;
