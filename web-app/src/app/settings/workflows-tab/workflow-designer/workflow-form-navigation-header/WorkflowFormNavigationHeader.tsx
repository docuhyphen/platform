import {Button, Text} from "@fluentui/react-components";
import {BackIcon} from "../../../../components/IconBundles.tsx";
import {useWorkflowFormNavigationHeaderStyles} from "./WorkflowFormNavigationHeaderStyles.tsx";

interface Props
{
    label: string;
    onBack: () => void;
}

const WorkflowFormNavigationHeader = ({label, onBack}: Props) =>
{
    const styles = useWorkflowFormNavigationHeaderStyles();
    const slug = label.toLowerCase().replace(/ /g, "-");

    return (
        <div id={"workflow-designer-form-navigation-header"}
             className={styles.formNavigationHeader}>
            <Button
                id={`workflow-form-back-${slug}`}
                size="small"
                appearance="subtle"
                shape="circular"
                icon={<BackIcon/>}
                aria-label={`Back from ${label}`}
                onClick={onBack}
            />
            <Text
                id={`workflow-form-heading-${slug}`}
                weight="semibold"
                size={400}
            >
                {label}
            </Text>
        </div>
    );
};

export default WorkflowFormNavigationHeader;
