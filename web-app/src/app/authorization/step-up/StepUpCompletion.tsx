import React, {useEffect} from "react";
import {useNavigate} from "react-router-dom";
import {Spinner, Text} from "@fluentui/react-components";
import {useStepUpStyles} from "./StepUpStyles.tsx";

const StepUpCompletion: React.FC = () =>
{
    const navigate = useNavigate();
    const styles = useStepUpStyles();

    useEffect(() =>
    {
        const target = "/exchanges";
        // Short handoff page after OAuth callback; users can retry the original action.
        const timer = window.setTimeout(() => navigate(target), 1200);
        return () => window.clearTimeout(timer);
    }, [navigate]);

    return (
        <div className={styles.stepUpContainer}>
            <Spinner/>
            <Text>Step-up verification completed. Returning to your session...</Text>
        </div>
    );
};

export default StepUpCompletion;

