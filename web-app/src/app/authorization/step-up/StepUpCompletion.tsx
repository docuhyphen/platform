import React, {useEffect} from "react";
import {useNavigate} from "react-router-dom";
import {Spinner, Text} from "@fluentui/react-components";

const StepUpCompletion: React.FC = () =>
{
    const navigate = useNavigate();

    useEffect(() =>
    {
        const target = "/sharing-sessions";
        // Short handoff page after OAuth callback; users can retry the original action.
        const timer = window.setTimeout(() => navigate(target), 1200);
        return () => window.clearTimeout(timer);
    }, [navigate]);

    return (
        <div style={{display: "flex", flexDirection: "column", gap: 12, alignItems: "center", marginTop: 80}}>
            <Spinner/>
            <Text>Step-up verification completed. Returning to your session...</Text>
        </div>
    );
};

export default StepUpCompletion;

