import {Button, Text} from "@fluentui/react-components";
import {TemplateAddIcon} from "../../components/IconBundles.tsx";
import {useTemplatesTabStyles} from "./TemplatesTabStyles.tsx";

const TemplatesTab = () =>
{
    const styles = useTemplatesTabStyles();

    return <>
        <div className={styles.tabContainer}>
            <div className={styles.header}>
                <div></div>
                <Button icon={<TemplateAddIcon/>}
                        appearance="secondary"
                        shape={"circular"}>
                    Create
                </Button>
            </div>
            <div>
                <Text size={500}>
                    We sincerely apologise, this feature is currently disabled due to known issues we're hoping to resolve soon.
                </Text>
            </div>
            <div>
                <Text size={500}>
                    Thank you for understanding
                </Text>
            </div>
        </div>
    </>
}

export default TemplatesTab;