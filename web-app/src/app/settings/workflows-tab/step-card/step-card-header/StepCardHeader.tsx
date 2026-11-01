import {Button} from "@fluentui/react-components";
import {BackIcon, DeleteIcon} from "../../../../components/IconBundles.tsx";
import {useStepCardHeaderStyles} from "./StepCardHeaderStyles.tsx";

interface StepCardHeaderProps
{
    index: number;
    onBack: () => void;
    onDelete: () => void;
}

const StepCardHeader = ({index, onBack, onDelete}: StepCardHeaderProps) =>
{
    const styles = useStepCardHeaderStyles();

    return (
        <div id={"step-card-div-4"}
             className={styles.cardHeader}>
            <Button id={`step-card-back-btn-${index}`}
                    size="small"
                    appearance="subtle"
                    shape="circular"
                    icon={<BackIcon/>}
                    aria-label="Back to steps"
                    onClick={onBack} />
            <Button id={`step-card-delete-btn-${index}`}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<DeleteIcon/>}
                    onClick={onDelete}
                    aria-label="Remove step" />
        </div>
    );
};

export default StepCardHeader;
