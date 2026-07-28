import React from "react";
import {Navigate} from "react-router-dom";
import {useAuth} from "../../context/AuthContext.tsx";
import {Capability} from "../models/models.tsx";
import AuthBootstrapSplash from "./AuthBootstrapSplash.tsx";

interface CapabilityProtectedContentProps
{
    capability: Capability;
    element: React.ReactElement;
    deniedPath?: string;
}

const CapabilityProtectedContent = ({
    capability,
    element,
    deniedPath = "/exchanges",
}: CapabilityProtectedContentProps) =>
{
    const {currentSession, hasCapability} = useAuth();

    if (!currentSession)
    {
        return <AuthBootstrapSplash/>;
    }

    return hasCapability(capability)
        ? element
        : <Navigate to={deniedPath} replace/>;
};

export default CapabilityProtectedContent;
