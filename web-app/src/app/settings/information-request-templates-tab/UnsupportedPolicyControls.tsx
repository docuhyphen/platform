import {Text} from "@fluentui/react-components";
import {InformationRequestTemplateUnsupportedPolicyControlDto} from "../../models/models.tsx";
import {useInformationRequestTemplatesTabStyles} from "./InformationRequestTemplatesTabStyles.tsx";

interface Props
{
    controls: InformationRequestTemplateUnsupportedPolicyControlDto[];
}

const UnsupportedPolicyControls = ({controls}: Props) =>
{
    const styles = useInformationRequestTemplatesTabStyles();

    if (controls.length === 0) return null;

    return (
        <div
            id={"information-request-template-unsupported-controls"}
            className={styles.unsupportedList}
        >
            {controls.map(control => (
                <div
                    key={control.controlKey}
                    className={styles.unsupportedItem}
                >
                    <button
                        id={`information-request-template-unsupported-${control.controlKey}`}
                        className={styles.disabledControl}
                        disabled
                        type={"button"}
                    >
                        {control.label}
                    </button>
                    <Text className={styles.secondaryText}>{control.reason}</Text>
                </div>
            ))}
        </div>
    );
};

export default UnsupportedPolicyControls;
