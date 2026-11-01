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
        const timer = window.setTimeout(() => navigate(target), 1200);
        return () => window.clearTimeout(timer);
    }, [navigate]);

    return (
        <div id={"step-up-completion-div-1"}
             className={styles.stepUpContainer}>
            <Spinner id={"step-up-completion-spinner-1"}/>
            <Text id={"step-up-completion-text-1"}>Step-up verification completed. Returning to your session...</Text>
        </div>
    );
};

export default StepUpCompletion;

